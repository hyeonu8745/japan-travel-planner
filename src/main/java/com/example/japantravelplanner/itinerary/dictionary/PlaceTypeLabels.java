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
 * Google Places type → 한국어 이름표 (resources/data/type_labels.csv)
 * 예: buddhist_temple → 사찰, point_of_interest → (표시 안 함)
 */
@Slf4j
@Component
public class PlaceTypeLabels {

    private static final String DEFAULT_PATH = "data/type_labels.csv";

    private record Label(String text, boolean show) {
    }

    private final Map<String, Label> labels = new HashMap<>();

    public PlaceTypeLabels() {
        this(new ClassPathResource(DEFAULT_PATH));
    }

    PlaceTypeLabels(Resource resource) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            reader.readLine();   // type,label_ko,show
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> fields = CsvParser.parseLine(line);
                if (fields.size() < 3) continue;
                labels.put(fields.get(0), new Label(fields.get(1), "Y".equalsIgnoreCase(fields.get(2))));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("관광지 이름표를 읽지 못했습니다.", e);
        }
        log.info("[PlaceTypeLabels] type {}개 로드", labels.size());
    }

    /**
     * 쉼표로 이어진 types를 화면에 보여줄 한국어 이름 목록으로 바꾼다.
     * 예: "buddhist_temple,tourist_attraction,point_of_interest" → [사찰, 관광명소]
     * - 표시하지 않는 type(show=N)과 사전에 없는 type은 뺀다
     * - 같은 이름이 여러 번 나오면 한 번만
     */
    public List<String> toVisibleLabels(String typesCsv) {
        if (typesCsv == null || typesCsv.isBlank()) return List.of();

        Set<String> result = new LinkedHashSet<>();
        for (String type : typesCsv.split(",")) {
            Label label = labels.get(type.trim());
            if (label != null && label.show()) {
                result.add(label.text());
            }
        }
        return List.copyOf(result);
    }
}