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
 * - AI가 놓친 것 다시 찾기 (11-18): 같은 규칙으로 문장을 훑어 1차에 없는 의심 문장(ReviewSuspects)만 AI에 다시 묻고,
 *   확인된 것도 같은 검사를 거친다. 1차와 합쳐 timeoutSeconds 안에서 끝나고, 시간이 모자라면 1차 결과만
 * - 실패해도 상세는 정상: AI 오류·형식 오류·시간 초과면 null (분석만 빠진다)
 */
@Slf4j
@Component
public class ReviewInsightAnalyzer {

    static final int MAX_OUTPUT_TOKENS = 800;
    static final int MAX_EVIDENCE_LENGTH = 60;   // 프롬프트는 40자, 여유를 둔다
    /** 같은 리뷰면 같은 결과 (같은 리뷰를 두 번 불렀더니 한 번은 불친절을 찾고 한 번은 못 찾았다) */
    static final double TEMPERATURE = 0.0;
    /** 다시 묻기 응답 길이 (문장 8개 × 짧은 답) */
    static final int RECHECK_OUTPUT_TOKENS = 600;
    /** 1차가 끝나고 남은 시간이 이보다 적으면 다시 묻지 않는다 */
    static final long MIN_RECHECK_MILLIS = 1_500;
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

    /** @param props 사용 여부, 모델, 최대 리뷰 수 등 */
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
        Steps steps = analyzeSteps(reviews);
        return steps == null ? null : steps.result();
    }

    /**
     * 1차 결과와 다시 묻기까지 합친 결과 (정확도 평가용: ReviewInsightLiveEval 이 둘을 비교한다)
     * @param first  1차 AI + 서버 확인만
     * @param result 다시 묻기까지 (화면에 주는 것)
     */
    record Steps(ReviewInsightsDto first, ReviewInsightsDto result) {
    }

    /** analyze 와 같고, 1차 결과도 같이 돌려준다 */
    Steps analyzeSteps(List<ReviewDto> reviews) {
        if (!props.isEnabled() || reviews == null || reviews.isEmpty()) return null;
        // 분석 대상이 아닌 리뷰는 빈 글로 둔다 (번호는 google.reviews 위치 그대로 유지, 빈 글에는 근거가 맞을 수 없다)
        List<String> texts = reviews.stream().map(r -> isKoreanOriginal(r) ? r.text() : "").toList();
        if (texts.stream().allMatch(String::isBlank)) {
            ReviewInsightsDto none = new ReviewInsightsDto(List.of(), List.of(), NO_KOREAN_NOTICE, 0);
            return new Steps(none, none);
        }

        String input = buildInput(reviews);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(props.getTimeoutSeconds());
        Future<AiResult> future = EXECUTOR.submit(() -> openAiClient.generateJson(AiModelTier.BASIC,
                ReviewInsightPrompt.INSTRUCTIONS, input, ReviewInsightPrompt.format(), MAX_OUTPUT_TOKENS, TEMPERATURE));
        try {
            AiResult ai = future.get(props.getTimeoutSeconds(), TimeUnit.SECONDS);
            List<Item> items = new ArrayList<>(readItems(jsonMapper, ai.text()));
            ReviewInsightsDto first = build(items, texts);
            ReviewInsightsDto result = recheck(first, items, texts, deadline);
            log.info("[ReviewInsight] 한국어 리뷰 {}개 분석 → 표시 {}건", result.analyzedReviewCount(), result.findings().size());
            return new Steps(first, result);
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

    /**
     * 2단계: 서버 규칙에 걸리는데 1차에 없는 문장(ReviewSuspects)만 AI에 다시 묻고, 확인된 것도 1차와 같은 검사를 거쳐 더한다.
     * 의심 문장이 없거나, 남은 시간이 모자라거나, 다시 묻기가 실패하면 1차 결과 그대로 (상세가 느려지지 않게)
     */
    private ReviewInsightsDto recheck(ReviewInsightsDto first, List<Item> items, List<String> texts, long deadline) {
        Set<String> found = new HashSet<>();
        first.findings().forEach(f -> found.add(f.reviewIndex() + ":" + f.tag()));
        List<ReviewSuspects.Suspect> suspects = ReviewSuspects.scan(texts, found);
        if (suspects.isEmpty()) return first;
        long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (remainingMs < MIN_RECHECK_MILLIS) {
            log.info("[ReviewInsight] 의심 문장 {}개, 남은 시간이 모자라 다시 묻지 않음", suspects.size());
            return first;
        }
        Future<AiResult> future = EXECUTOR.submit(() -> openAiClient.generateJson(AiModelTier.BASIC,
                ReviewRecheckPrompt.INSTRUCTIONS, ReviewRecheckPrompt.input(suspects), ReviewRecheckPrompt.format(),
                RECHECK_OUTPUT_TOKENS, TEMPERATURE));
        try {
            AiResult ai = future.get(remainingMs, TimeUnit.MILLISECONDS);
            List<Item> confirmed = recheckItems(jsonMapper, ai.text(), suspects);
            if (confirmed.isEmpty()) {
                log.info("[ReviewInsight] 의심 문장 {}개 → 확인 0건", suspects.size());
                return first;
            }
            List<Item> all = new ArrayList<>(items);
            all.addAll(confirmed);
            ReviewInsightsDto merged = build(all, texts);
            log.info("[ReviewInsight] 의심 문장 {}개 → AI 확인 {}건 → 더함 {}건", suspects.size(), confirmed.size(),
                    merged.findings().size() - first.findings().size());
            return merged;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return first;
        } catch (Exception e) {
            future.cancel(true);
            log.info("[ReviewInsight] 다시 묻기 실패, 1차 결과만 씁니다: {}", e.getClass().getSimpleName());
            return first;
        }
    }

    /** 다시 묻기 응답 → 1차와 같은 모양(리뷰 번호 + 종류 + 근거). 확인(confirmed)된 것만, 없는 id 는 버린다 */
    static List<Item> recheckItems(JsonMapper mapper, String json, List<ReviewSuspects.Suspect> suspects) {
        RecheckResult result = mapper.readValue(json, RecheckResult.class);
        List<Item> items = new ArrayList<>();
        if (result == null || result.items() == null) return items;
        for (RecheckItem r : result.items()) {
            if (r == null || !r.confirmed() || r.id() < 0 || r.id() >= suspects.size()) continue;
            ReviewSuspects.Suspect s = suspects.get(r.id());
            items.add(new Item(s.index(), List.of(new RawFinding(s.tag().name(), r.evidence()))));
        }
        return items;
    }

    /** AI 응답 → 화면용. 없는 리뷰 번호, 모르는 종류, 리뷰에 없는 근거, 같은 리뷰의 같은 종류 중복은 버린다 */
    static ReviewInsightsDto parse(JsonMapper mapper, String json, List<String> texts) {
        return build(readItems(mapper, json), texts);
    }

    /** 1차 응답 JSON → 리뷰별 후보 */
    static List<Item> readItems(JsonMapper mapper, String json) {
        Result result = mapper.readValue(json, Result.class);
        return result == null || result.reviews() == null ? List.of() : result.reviews();
    }

    /** 후보(1차 + 다시 묻기) → 화면용. 근거가 리뷰에 있는지, 근거 앞뒤가 종류 규칙에 맞는지 서버가 확인한다 */
    static ReviewInsightsDto build(List<Item> items, List<String> texts) {
        List<Finding> findings = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        // 버린 이유별 개수 (튜닝용 로그. 리뷰 글은 Google 내용이라 로그에도 남기지 않는다)
        Map<String, Integer> dropped = new java.util.LinkedHashMap<>();
        int proposed = 0;

        for (Item item : items) {
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
            }
        }
        int beforeOverlap = findings.size();
        findings = dropCoveredByDiscrimination(findings, texts);
        if (findings.size() < beforeOverlap) dropped.merge("차별과 같은 문장", beforeOverlap - findings.size(), Integer::sum);
        Map<ReviewTag, Set<Integer>> reviewsByTag = new EnumMap<>(ReviewTag.class);
        for (Finding f : findings) {
            reviewsByTag.computeIfAbsent(ReviewTag.valueOf(f.tag()), t -> new HashSet<>()).add(f.reviewIndex());
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

    /**
     * 차별로 찾은 문장과 같은 문장에 붙은 불친절·좋은 점은 뺀다 (11-18 실제 AI 평가).
     * "외국인이라 무시하는 태도"는 차별 하나로 보여 주면 되고, "한국어 메뉴 달라고 했더니 비웃으면서"는 좋은 점이 아니다.
     * 다른 문장이면 둘 다 둔다 ("한국어 메뉴는 있었다. 그런데 한국인이라고 구석 자리로").
     */
    static List<Finding> dropCoveredByDiscrimination(List<Finding> findings, List<String> texts) {
        Set<String> discriminationSentences = new HashSet<>();
        for (Finding f : findings) {
            if (!ReviewTag.DISCRIMINATION.name().equals(f.tag())) continue;
            int sentence = sentenceOf(texts.get(f.reviewIndex()), f.evidence());
            if (sentence >= 0) discriminationSentences.add(f.reviewIndex() + ":" + sentence);
        }
        if (discriminationSentences.isEmpty()) return findings;
        List<Finding> kept = new ArrayList<>();
        for (Finding f : findings) {
            boolean covered = (ReviewTag.UNFRIENDLY.name().equals(f.tag()) || ReviewTag.KOREAN_FRIENDLY.name().equals(f.tag()))
                    && discriminationSentences.contains(f.reviewIndex() + ":" + sentenceOf(texts.get(f.reviewIndex()), f.evidence()));
            if (!covered) kept.add(f);
        }
        return kept;
    }

    /** 근거가 들어 있는 문장 번호 (ReviewSuspects 의 문장 나누기 기준). 여러 문장에 걸치면 -1 */
    private static int sentenceOf(String text, String evidence) {
        List<String> sentences = ReviewSuspects.sentences(text);
        // 문장 나누기가 끝의 마침표를 떼므로 근거 앞뒤의 문장부호도 뗀다 ("…노골적이었어요." → 11-18 2차 평가에서 빠지지 않던 원인)
        String target = squeeze(evidence).replaceAll("^[.!?。！？~^ㅠㅜ]+|[.!?。！？~^ㅠㅜ]+$", "");
        if (target.isEmpty()) return -1;
        for (int i = 0; i < sentences.size(); i++) {
            if (squeeze(sentences.get(i)).contains(target)) return i;
        }
        return -1;
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RecheckResult(List<RecheckItem> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RecheckItem(int id, boolean confirmed, String evidence) {
    }
}
