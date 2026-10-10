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
 * - 저장하지 않는다: 리뷰도 분석 결과도 Google 내용에서 나온 값이라 상세를 열 때마다 새로 만든다
 * - 가게를 단정하지 않는다: 리뷰별 경험과 근거 구절만 주고, 판정 문장은 만들지 않는다
 * - AI가 지어내지 못하게: 근거(evidence)가 실제 리뷰 글에 그대로 있는 것만 남긴다
 * - AI가 잘못 붙이지 못하게: 근거에 종류별 핵심 말(ReviewTag.keywords)이 있어야 한다 (차별은 국적·외국인 언급 필수)
 * - 실패해도 상세는 정상: AI 오류·형식 오류·시간 초과면 null (분석만 빠진다)
 */
@Slf4j
@Component
public class ReviewInsightAnalyzer {

    static final int MAX_OUTPUT_TOKENS = 800;
    static final int MAX_EVIDENCE_LENGTH = 60;   // 프롬프트는 40자, 여유를 둔다
    static final String NOTICE = "AI가 Google 리뷰 최대 5개를 읽고 찾은 참고 정보예요. 리뷰 몇 개로 가게 전체를 판단할 수는 없어요.";

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

    /** @return 분석 결과. 꺼져 있거나, 리뷰가 없거나, 실패하면 null */
    public ReviewInsightsDto analyze(List<ReviewDto> reviews) {
        if (!props.isEnabled() || reviews == null || reviews.isEmpty()) return null;
        List<String> texts = reviews.stream().map(r -> r == null || r.text() == null ? "" : r.text()).toList();
        if (texts.stream().allMatch(String::isBlank)) return null;

        String input = buildInput(reviews);
        Future<AiResult> future = EXECUTOR.submit(() -> openAiClient.generateJson(AiModelTier.BASIC,
                ReviewInsightPrompt.INSTRUCTIONS, input, ReviewInsightPrompt.format(), MAX_OUTPUT_TOKENS));
        try {
            AiResult ai = future.get(props.getTimeoutSeconds(), TimeUnit.SECONDS);
            ReviewInsightsDto result = parse(jsonMapper, ai.text(), texts);
            log.info("[ReviewInsight] 리뷰 {}개 → 표시 {}건", texts.size(), result.findings().size());
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

    /** 리뷰를 구분자 안에 넣는다. 리뷰 글 속 '<'는 바꿔서 구분자를 흉내 내지 못하게 한다 */
    static String buildInput(List<ReviewDto> reviews) {
        StringBuilder sb = new StringBuilder("[리뷰 목록]\n");
        for (int i = 0; i < reviews.size(); i++) {
            ReviewDto r = reviews.get(i);
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

        if (result != null && result.reviews() != null) {
            for (Item item : result.reviews()) {
                if (item == null || item.index() < 0 || item.index() >= texts.size() || item.findings() == null) continue;
                String review = squeeze(texts.get(item.index()));
                for (RawFinding raw : item.findings()) {
                    ReviewTag tag = raw == null ? null : ReviewTag.from(raw.tag());
                    String evidence = raw == null || raw.evidence() == null ? "" : raw.evidence().strip();
                    if (tag == null || evidence.isEmpty() || evidence.length() > MAX_EVIDENCE_LENGTH) continue;
                    if (!review.contains(squeeze(evidence))) continue;   // 리뷰에 없는 말 = 지어낸 것
                    if (!tag.supportedBy(evidence)) continue;            // 근거에 그 종류의 핵심 말이 없음 = 잘못 붙인 것
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
        return new ReviewInsightsDto(List.copyOf(findings), List.copyOf(counts), NOTICE);
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
