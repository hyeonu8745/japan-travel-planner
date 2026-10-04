package com.example.japantravelplanner.itinerary.narration;

import com.example.japantravelplanner.ai.client.AiModelTier;
import com.example.japantravelplanner.ai.client.AiResult;
import com.example.japantravelplanner.ai.client.OpenAiClient;
import com.example.japantravelplanner.global.exception.AiServiceException;
import com.example.japantravelplanner.itinerary.planner.ScheduledDay;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 완성된 일정의 날짜별 설명(2~3문장)을 Terra로 쓴다.
 * AI는 이미 정해진 일정을 글로 풀어쓰기만 한다. 장소·순서·시간은 건드리지 않는다.
 *
 * 설명은 없어도 일정은 정상이므로, 실패하면 예외를 던지지 않고 빈 결과를 돌려준다.
 * (AI 오류, 형식 오류, 규칙을 어긴 설명 모두 해당 날의 설명만 빠진다)
 * 여러 날을 한 번에 요청해서 호출은 일정 하나당 한 번이다.
 */
@Slf4j
@Component
public class ItineraryNarrator {

    // 출력 한도: 추론 토큰도 포함되므로 날수에 비례해서 넉넉하게
    static final int BASE_OUTPUT_TOKENS = 1000;
    static final int TOKENS_PER_DAY = 300;

    private final OpenAiClient openAiClient;
    private final JsonMapper jsonMapper;

    public ItineraryNarrator(OpenAiClient openAiClient, JsonMapper jsonMapper) {
        this.openAiClient = openAiClient;
        this.jsonMapper = jsonMapper;
    }

    /**
     * @param typeLabels Google type 문자열 → 화면에 보이는 한국어 이름 (PlaceTypeLabels::toVisibleLabels)
     * @return 날짜 번호 → 설명. 설명이 없는 날은 들어 있지 않다
     */
    public Map<Integer, String> narrate(TripBrief brief, List<ScheduledDay> days,
                                        Function<String, List<String>> typeLabels) {
        List<ScheduledDay> targets = days.stream().filter(d -> !d.stops().isEmpty()).toList();
        if (targets.isEmpty()) {
            return Map.of();
        }

        String input = NarrationInputBuilder.build(brief, targets, typeLabels);
        int maxTokens = BASE_OUTPUT_TOKENS + TOKENS_PER_DAY * targets.size();
        Set<Integer> validDays = targets.stream().map(ScheduledDay::dayNumber).collect(Collectors.toSet());

        try {
            AiResult ai = openAiClient.generateJson(AiModelTier.ADVANCED, NarrationPrompt.INSTRUCTIONS,
                    input, NarrationSchema.format(), maxTokens);
            Map<Integer, String> result = parse(jsonMapper, ai.text(), validDays);
            log.info("[Narration] {}일 중 {}일 설명 생성", targets.size(), result.size());
            return result;
        } catch (AiServiceException | JacksonException e) {
            log.warn("[Narration] 설명 생성 실패, 설명 없이 진행합니다: {}", e.getMessage());
            return Map.of();
        }
    }

    /** AI 응답을 날짜별 설명으로 바꾼다. 없는 날짜 번호, 중복, 규칙을 어긴 설명은 버린다 */
    static Map<Integer, String> parse(JsonMapper mapper, String json, Set<Integer> validDays) {
        NarrationResult result = mapper.readValue(json, NarrationResult.class);
        Map<Integer, String> descriptions = new HashMap<>();
        if (result == null || result.days() == null) {
            return descriptions;
        }
        for (NarrationResult.DayText item : result.days()) {
            if (!validDays.contains(item.day()) || descriptions.containsKey(item.day())) {
                continue;
            }
            String cleaned = DescriptionGuard.clean(item.description());
            if (cleaned != null) {
                descriptions.put(item.day(), cleaned);
            }
        }
        return descriptions;
    }
}