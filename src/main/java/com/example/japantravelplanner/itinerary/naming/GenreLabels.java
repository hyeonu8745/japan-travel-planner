package com.example.japantravelplanner.itinerary.naming;

import com.example.japantravelplanner.global.util.CsvParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 타베로그 장르(일본어) → 한국어 이름표 (resources/data/genre_labels.csv, 데이터의 장르 222개 전부).
 * 화면 칩과 AI 하루 설명이 같은 이름을 쓴다. 10-10: 설명 AI가 장르를 그때그때 옮겨서 とんかつ가 "돈카쓰"가 됐다.
 * 표기는 여행자가 실제로 부르는 이름 (사용자 의견: 돈카츠·츠케멘·야키토리. 국립국어원식 "돈가스"·"쓰케멘"은 쓰지 않는다).
 * 순수 계산이라 정적 메서드로 쓴다 (응답 DTO 변환이 정적이라서).
 */
public final class GenreLabels {

    static final String PATH = "/data/genre_labels.csv";
    private static final Map<String, String> LABELS = load();

    private GenreLabels() {
    }

    /**
     * "つけ麺,ラーメン" → [츠케멘, 라멘]. 같은 이름은 한 번만(日本料理·和食（その他） → 일식),
     * 표에 없는 장르는 원문 그대로 둔다 (빠뜨리는 것보다 낫다)
     */
    public static List<String> of(String genreCsv) {
        if (genreCsv == null || genreCsv.isBlank()) return List.of();
        Set<String> result = new LinkedHashSet<>();
        for (String g : genreCsv.split("[,、]")) {
            String genre = g.trim();
            if (!genre.isEmpty()) result.add(LABELS.getOrDefault(genre, genre));
        }
        return List.copyOf(result);
    }

    /** 한 줄로 ("츠케멘·라멘"). 장르가 없으면 null */
    public static String joined(String genreCsv) {
        List<String> labels = of(genreCsv);
        return labels.isEmpty() ? null : String.join("·", labels);
    }

    static boolean has(String genre) {
        return LABELS.containsKey(genre);
    }

    private static Map<String, String> load() {
        Map<String, String> labels = new HashMap<>();
        try (InputStream in = GenreLabels.class.getResourceAsStream(PATH)) {
            if (in == null) throw new IOException(PATH + " 없음");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            reader.readLine();   // genre_ja,label_ko
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> fields = CsvParser.parseLine(line);
                if (fields.size() >= 2) labels.put(fields.get(0).trim(), fields.get(1).trim());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("장르 이름표를 읽지 못했습니다.", e);
        }
        return Map.copyOf(labels);
    }
}
