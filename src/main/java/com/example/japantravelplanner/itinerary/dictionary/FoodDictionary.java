package com.example.japantravelplanner.itinerary.dictionary;

import com.example.japantravelplanner.global.util.CsvParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 한국어 음식 표현 → 타베로그 장르 사전 (resources/data/food_dictionary.csv)
 * 예: "라멘" PREFER → {ラーメン, つけ麺, 担々麺, ...}
 */
@Slf4j
@Component
public class FoodDictionary {

    private static final String DEFAULT_PATH = "data/food_dictionary.csv";

    // 표현(소문자) + 종류 → 장르 목록
    private final Map<FoodPreferenceType, Map<String, Set<String>>> entries =
            new EnumMap<>(FoodPreferenceType.class);

    // 긴 표현부터 찾기 위한 정렬 목록 ("야키토리"를 "토리"보다 먼저)
    private final Map<FoodPreferenceType, List<String>> keywordsByLength =
            new EnumMap<>(FoodPreferenceType.class);

    public FoodDictionary() {
        this(new ClassPathResource(DEFAULT_PATH));
    }

    // 테스트에서 다른 파일을 넣을 수 있게 열어둔 생성자
    FoodDictionary(Resource resource) {
        for (FoodPreferenceType type : FoodPreferenceType.values()) {
            entries.put(type, new HashMap<>());
        }
        load(resource);
        for (FoodPreferenceType type : FoodPreferenceType.values()) {
            List<String> keywords = new ArrayList<>(entries.get(type).keySet());
            keywords.sort(Comparator.comparingInt(String::length).reversed());
            keywordsByLength.put(type, keywords);
        }
        log.info("[FoodDictionary] 표현 {}개 로드 (PREFER {}, AVOID {})",
                entries.values().stream().mapToInt(Map::size).sum(),
                entries.get(FoodPreferenceType.PREFER).size(),
                entries.get(FoodPreferenceType.AVOID).size());
    }

    private void load(Resource resource) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String header = reader.readLine();   // korean_keyword,genres,type,note
            if (header == null) {
                throw new IllegalStateException("음식 사전이 비어 있습니다.");
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> fields = CsvParser.parseLine(line);
                if (fields.size() < 3) continue;

                String keyword = fields.get(0).toLowerCase(Locale.ROOT);
                FoodPreferenceType type = FoodPreferenceType.valueOf(fields.get(2));
                Set<String> genres = new LinkedHashSet<>();
                for (String genre : fields.get(1).split("\\|")) {
                    if (!genre.isBlank()) genres.add(genre.trim());
                }
                entries.get(type).computeIfAbsent(keyword, k -> new LinkedHashSet<>()).addAll(genres);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("음식 사전을 읽지 못했습니다.", e);
        }
    }

    /** 표현 하나에 해당하는 장르 */
    public Set<String> genresOf(String keyword, FoodPreferenceType type) {
        if (keyword == null) return Set.of();
        return Collections.unmodifiableSet(
                entries.get(type).getOrDefault(keyword.trim().toLowerCase(Locale.ROOT), Set.of()));
    }

    /**
     * 문장 안에 들어 있는 표현을 모두 찾아 장르를 모은다.
     * 예: "라멘이랑 야키토리 좋아해요" (PREFER) → ラーメン, つけ麺, ..., 焼鳥, 串焼き
     * AI가 정리한 조건 설명(PreferenceConstraint.description)을 넣는 용도
     */
    public Set<String> findGenres(String text, FoodPreferenceType type) {
        if (text == null || text.isBlank()) return Set.of();

        String remaining = text.toLowerCase(Locale.ROOT);
        Set<String> result = new LinkedHashSet<>();
        for (String keyword : keywordsByLength.get(type)) {
            if (remaining.contains(keyword)) {
                result.addAll(entries.get(type).get(keyword));
                // 이미 찾은 부분은 지워서 짧은 표현이 다시 걸리지 않게 ("야키토리" 안의 "토리")
                remaining = remaining.replace(keyword, " ");
            }
        }
        return result;
    }
}