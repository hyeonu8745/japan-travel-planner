package com.example.japantravelplanner.itinerary.naming;

import com.example.japantravelplanner.ai.client.AiModelTier;
import com.example.japantravelplanner.ai.client.AiResult;
import com.example.japantravelplanner.ai.client.OpenAiClient;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 이름 목록을 AI(gpt-4o-mini)로 한국어 이름으로 바꾼다. 저장은 하지 않는다 (KoreanNameService).
 * AI가 규칙을 어긴 이름(한자·가나가 남음, 너무 김, 한글이 없음)은 버린다 → 그 장소는 원래 이름으로 보인다.
 * 실패해도 예외를 던지지 않고 빈 결과 (이름이 없어도 일정은 정상).
 */
@Slf4j
@Component
public class KoreanNameTranslator {

    static final int MAX_LENGTH = 60;
    static final int OUTPUT_TOKENS_PER_NAME = 40;
    private static final Pattern JAPANESE = Pattern.compile("[\\p{IsHiragana}\\p{IsKatakana}\\p{IsHan}]");
    private static final Pattern HANGUL = Pattern.compile("[가-힣]");
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final OpenAiClient openAiClient;
    private final JsonMapper jsonMapper;
    private final KoreanNameProperties props;

    public KoreanNameTranslator(OpenAiClient openAiClient, JsonMapper jsonMapper, KoreanNameProperties props) {
        this.openAiClient = openAiClient;
        this.jsonMapper = jsonMapper;
        this.props = props;
    }

    /**
     * @param names 키("a12" = 관광지 12, "r34" = 식당 34) → 원래 이름
     * @return 키 → 한국어 이름 (규칙을 지킨 것만)
     */
    public Map<String, String> translate(Map<String, String> names) {
        Map<String, String> result = new LinkedHashMap<>();
        if (names.isEmpty()) return result;
        List<Map.Entry<String, String>> entries = new ArrayList<>(names.entrySet());
        for (int from = 0; from < entries.size(); from += props.getMaxPerCall()) {
            List<Map.Entry<String, String>> chunk = entries.subList(from, Math.min(entries.size(), from + props.getMaxPerCall()));
            result.putAll(translateChunk(chunk));
        }
        return result;
    }

    private Map<String, String> translateChunk(List<Map.Entry<String, String>> chunk) {
        String input = buildInput(chunk);
        int maxTokens = 200 + chunk.size() * OUTPUT_TOKENS_PER_NAME;
        Future<AiResult> future = EXECUTOR.submit(() -> openAiClient.generateJson(AiModelTier.BASIC,
                KoreanNamePrompt.INSTRUCTIONS, input, KoreanNamePrompt.format(), maxTokens, 0.0));
        try {
            AiResult ai = future.get(props.getTimeoutSeconds(), TimeUnit.SECONDS);
            Map<String, String> originals = new LinkedHashMap<>();
            chunk.forEach(e -> originals.put(e.getKey(), e.getValue()));
            Map<String, String> parsed = parse(jsonMapper, ai.text(), originals);
            log.info("[KoreanName] {}개 요청 → {}개 사용", chunk.size(), parsed.size());
            return parsed;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return Map.of();
        } catch (Exception e) {
            future.cancel(true);
            log.warn("[KoreanName] 실패, 원래 이름으로 진행합니다: {}", e.getClass().getSimpleName());
            return Map.of();
        }
    }

    /** 이름 속 '<'·'>'는 바꿔서 구분자를 흉내 내지 못하게 한다 */
    static String buildInput(List<Map.Entry<String, String>> chunk) {
        StringBuilder sb = new StringBuilder("[이름 목록]\n");
        for (Map.Entry<String, String> e : chunk) {
            String name = e.getValue() == null ? "" : e.getValue().replace("<", "＜").replace(">", "＞");
            sb.append("<place id=\"").append(e.getKey()).append("\">").append(name).append("</place>\n");
        }
        return sb.toString();
    }

    /** 요청한 id만, 규칙을 지킨 이름만 */
    static Map<String, String> parse(JsonMapper mapper, String json, Map<String, String> originals) {
        Result result = mapper.readValue(json, Result.class);
        Map<String, String> names = new LinkedHashMap<>();
        if (result == null || result.items() == null) return names;
        for (Item item : result.items()) {
            if (item == null || item.id() == null || !originals.containsKey(item.id())) continue;
            String name = clean(item.nameKo());
            if (isValid(name)) names.putIfAbsent(item.id(), name);
        }
        return names;
    }

    static String clean(String name) {
        return name == null ? "" : name.replaceAll("\\s+", " ").strip();
    }

    /** 한글이 있고, 한자·가나가 없고, 너무 길지 않은 이름 */
    static boolean isValid(String name) {
        return name != null && !name.isEmpty() && name.length() <= MAX_LENGTH
                && HANGUL.matcher(name).find() && !JAPANESE.matcher(name).find();
    }

    record Result(List<Item> items) {
    }

    record Item(String id, @JsonProperty("name_ko") String nameKo) {
    }
}
