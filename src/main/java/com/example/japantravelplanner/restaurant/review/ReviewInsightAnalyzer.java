package com.example.japantravelplanner.restaurant.review;

import com.example.japantravelplanner.ai.client.AiModelTier;
import com.example.japantravelplanner.ai.client.AiResult;
import com.example.japantravelplanner.ai.client.OpenAiClient;
import com.example.japantravelplanner.attraction.dto.AttractionDetailResponseDto.ReviewDto;
import com.example.japantravelplanner.restaurant.dto.ReviewInsightsDto;
import com.example.japantravelplanner.restaurant.dto.ReviewInsightsDto.Finding;
import com.example.japantravelplanner.restaurant.dto.ReviewInsightsDto.TagCount;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 식당 상세의 Google 리뷰(최대 5개)를 AI(gpt-4o-mini)로 읽고 차별·불친절·위생·한국인에게 좋은 점을 찾는다.
 *
 * - 한국인이 쓴 리뷰만 읽는다: 한국어로 요청하면 Google이 한국어 리뷰가 모자란 만큼 일본어·영어 리뷰를 번역해서 채워 준다.
 *   번역 리뷰(원문이 한국어가 아님)는 분석에서 뺀다. 한국인의 경험만 "한국인 리뷰"로 표시하기 위해서다
 * - 저장하지 않는다: 리뷰도 분석 결과도 Google 내용에서 나온 값이라 상세를 열 때마다 새로 만든다
 * - 가게를 단정하지 않는다: 리뷰별 경험과 근거 구절만 주고, 판정 문장은 만들지 않는다
 * - AI가 지어내지 못하게: 근거(evidence)가 실제 리뷰 글에 그대로 있는 것만 남긴다
 * - AI가 잘못 붙이지 못하게: 근거 주변 글이 종류별 규칙(ReviewTag.supportedBy)을 만족해야 한다
 *   (차별은 국적 말 + 다르게 대한 말, 불친절은 직원 말 + 나쁜 태도 말, 부정된 말은 세지 않음)
 * - 실패해도 상세는 정상: AI 오류·형식 오류·시간 초과면 null (분석만 빠진다)
 */
@Slf4j
@Component
public class ReviewInsightAnalyzer {

    static final int MAX_OUTPUT_TOKENS = 800;
    static final int MAX_EVIDENCE_LENGTH = 60;   // 프롬프트는 40자, 여유를 둔다
    /** 같은 리뷰면 같은 결과 (10-10: 같은 리뷰를 두 번 불렀더니 한 번은 불친절을 찾고 한 번은 못 찾았다) */
    static final double TEMPERATURE = 0.0;
    /** 핵심 말은 근거 구절과 그 앞뒤 이만큼(띄어쓰기 뺀 글자 수)에서 찾는다 */
    static final int KEYWORD_WINDOW = 30;
    static final String NOTICE = "AI가 한국인이 쓴 Google 리뷰 %d개를 읽고 찾은 참고 정보예요. 리뷰 몇 개로 가게 전체를 판단할 수는 없어요.";
    static final String NO_KOREAN_NOTICE = "한국인이 쓴 리뷰가 없어서 리뷰 분석을 하지 않았어요.";

    private static final java.util.regex.Pattern HANGUL = java.util.regex.Pattern.compile("[가-힣]");

    /** 시간 초과 시 기다리지 않고 넘어가기 위해 따로 돌린다 (가상 스레드) */
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final OpenAiClient openAiClient;
    private final JsonMapper jsonMapper;
    private final ReviewInsightProperties props;

    public ReviewInsightAnalyzer(OpenAiClient openAiClient, JsonMapper jsonMapper, ReviewInsightProperties props) {
        this.openAiClient = openAiClient;
        this.jsonMapper = jsonMapper;
        this.props = props;
    }

    /**
     * @return 분석 결과. 꺼져 있거나, 리뷰가 없거나, 실패하면 null.
     *         리뷰는 있지만 한국인이 쓴 리뷰가 없으면 AI를 부르지 않고 analyzedReviewCount 0인 결과
     */
    public ReviewInsightsDto analyze(List<ReviewDto> reviews) {
        if (!props.isEnabled() || reviews == null || reviews.isEmpty()) return null;
        // 분석 대상이 아닌 리뷰는 빈 글로 둔다 (번호는 google.reviews 위치 그대로 유지, 빈 글에는 근거가 맞을 수 없다)
        List<String> texts = reviews.stream().map(r -> isKoreanOriginal(r) ? r.text() : "").toList();
        if (texts.stream().allMatch(String::isBlank)) {
            return new ReviewInsightsDto(List.of(), List.of(), NO_KOREAN_NOTICE, 0);
        }

        String input = buildInput(reviews);
        Future<AiResult> future = EXECUTOR.submit(() -> openAiClient.generateJson(AiModelTier.BASIC,
                ReviewInsightPrompt.INSTRUCTIONS, input, ReviewInsightPrompt.format(), MAX_OUTPUT_TOKENS, TEMPERATURE));
        try {
            AiResult ai = future.get(props.getTimeoutSeconds(), TimeUnit.SECONDS);
            ReviewInsightsDto result = parse(jsonMapper, ai.text(), texts);
            log.info("[ReviewInsight] 한국어 리뷰 {}개 분석 → 표시 {}건", result.analyzedReviewCount(), result.findings().size());
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return null;
        } catch (Exception e) {
            // 시간 초과, AI 오류, JSON 형식 오류 모두 분석만 빼고 진행
            future.cancel(true);
            log.warn("[ReviewInsight] 분석 실패, 분석 없이 진행합니다: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    /** 한국어로 쓴 리뷰인지. 원문 언어가 있으면 그것으로, 없으면 번역이 아니고 한글이 들어 있는지로 본다 */
    static boolean isKoreanOriginal(ReviewDto r) {
        if (r == null || r.text() == null || r.text().isBlank() || r.translated()) return false;
        if (r.originalLanguage() != null) return r.originalLanguage().toLowerCase(java.util.Locale.ROOT).startsWith("ko");
        return HANGUL.matcher(r.text()).find();
    }

    /**
     * 한국인이 쓴 리뷰만 구분자 안에 넣는다 (번호는 google.reviews 위치 그대로).
     * 리뷰 글 속 '<'는 바꿔서 구분자를 흉내 내지 못하게 한다
     */
    static String buildInput(List<ReviewDto> reviews) {
        StringBuilder sb = new StringBuilder("[리뷰 목록]\n");
        for (int i = 0; i < reviews.size(); i++) {
            ReviewDto r = reviews.get(i);
            if (!isKoreanOriginal(r)) continue;
            String text = r == null || r.text() == null ? "" : r.text().replace("<", "＜").replace(">", "＞");
            Integer rating = r == null ? null : r.rating();
            sb.append("<review index=\"").append(i).append("\" rating=\"").append(rating == null ? "" : rating)
                    .append("\">\n").append(text).append("\n</review>\n");
        }
        return sb.toString();
    }

    /** AI 응답 → 화면용. 없는 리뷰 번호, 모르는 종류, 리뷰에 없는 근거, 같은 리뷰의 같은 종류 중복은 버린다 */
    static ReviewInsightsDto parse(JsonMapper mapper, String json, List<String> texts) {
        Result result = mapper.readValue(json, Result.class);
        List<Finding> findings = new ArrayList<>();
        Map<ReviewTag, Set<Integer>> reviewsByTag = new EnumMap<>(ReviewTag.class);
        Set<String> seen = new HashSet<>();
        // 버린 이유별 개수 (튜닝용 로그. 리뷰 글은 Google 내용이라 로그에도 남기지 않는다)
        Map<String, Integer> dropped = new java.util.LinkedHashMap<>();
        int proposed = 0;

        if (result != null && result.reviews() != null) {
            for (Item item : result.reviews()) {
                if (item == null || item.findings() == null) continue;
                if (item.index() < 0 || item.index() >= texts.size()) {
                    dropped.merge("없는 리뷰 번호", item.findings().size(), Integer::sum);
                    continue;
                }
                String review = squeeze(texts.get(item.index()));
                for (RawFinding raw : item.findings()) {
                    proposed++;
                    ReviewTag tag = raw == null ? null : ReviewTag.from(raw.tag());
                    String evidence = raw == null || raw.evidence() == null ? "" : raw.evidence().strip();
                    if (tag == null || evidence.isEmpty() || evidence.length() > MAX_EVIDENCE_LENGTH) {
                        dropped.merge("형식", 1, Integer::sum);
                        continue;
                    }
                    int at = review.indexOf(squeeze(evidence));
                    if (at < 0) {   // 리뷰에 없는 말 = 지어낸 것
                        dropped.merge(tag.name() + ":근거가 리뷰에 없음", 1, Integer::sum);
                        continue;
                    }
                    // 근거와 그 앞뒤에 그 종류의 핵심 말이 없음 = 잘못 붙인 것
                    // (AI가 "친절한 편은 아니라"만 잘라 와도 바로 앞의 "점원들의 태도는"으로 인정)
                    String around = review.substring(Math.max(0, at - KEYWORD_WINDOW),
                            Math.min(review.length(), at + squeeze(evidence).length() + KEYWORD_WINDOW));
                    if (!tag.supportedBy(around)) {
                        dropped.merge(tag.name() + ":핵심 말 없음", 1, Integer::sum);
                        continue;
                    }
                    if (!seen.add(item.index() + ":" + tag)) continue;

                    findings.add(new Finding(item.index(), tag.name(), tag.label(), tag.caution(), evidence));
                    reviewsByTag.computeIfAbsent(tag, t -> new HashSet<>()).add(item.index());
                }
            }
        }
        findings.sort((a, b) -> a.reviewIndex() != b.reviewIndex()
                ? Integer.compare(a.reviewIndex(), b.reviewIndex())
                : Integer.compare(ReviewTag.valueOf(a.tag()).ordinal(), ReviewTag.valueOf(b.tag()).ordinal()));

        List<TagCount> counts = new ArrayList<>();
        for (ReviewTag tag : ReviewTag.values()) {
            Set<Integer> indexes = reviewsByTag.get(tag);
            if (indexes != null) counts.add(new TagCount(tag.name(), tag.label(), tag.caution(), indexes.size()));
        }
        if (proposed > 0) {
            log.info("[ReviewInsight] AI 후보 {}건 → 채택 {}건, 버림 {}", proposed, findings.size(), dropped);
        }
        int analyzed = (int) texts.stream().filter(t -> t != null && !t.isBlank()).count();
        return new ReviewInsightsDto(List.copyOf(findings), List.copyOf(counts), NOTICE.formatted(analyzed), analyzed);
    }

    /** 띄어쓰기·줄바꿈 차이는 무시하고 비교 */
    private static String squeeze(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result(List<Item> reviews) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(int index, List<RawFinding> findings) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawFinding(String tag, String evidence) {
    }
}
