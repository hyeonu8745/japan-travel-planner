package com.example.japantravelplanner.attraction.batch;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.attraction.repository.AttractionRepository;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.FileReader;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

/**
 * 관광지 CSV를 DB에 넣는다 (서버 시작 때마다).
 * DB에 없는 place_id만 추가하고, 이미 있는 관광지는 건드리지 않는다.
 *  - 일정 항목(itinerary_stops)이 관광지 id를 참조해서, 지우고 다시 넣으면 저장된 일정·이력이 깨진다
 *  - 10-10 수집 범위를 넓힌 CSV(검색어·구 추가)를 그대로 덮어써도 새 관광지만 들어간다
 * 예전에는 한 건이라도 있으면 통째로 건너뛰어서, CSV를 바꿔도 반영되지 않았다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(2) // Restaurant(1) 다음에 실행
public class AttractionDataImporter implements CommandLineRunner {

    private final AttractionRepository attractionRepository;

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    private static final String CSV_PATH = "data/processed/attractions_tokyo.csv";

    @Override
    public void run(String... args) throws Exception {
        if (!Paths.get(CSV_PATH).toFile().exists()) {
            log.warn("CSV 파일을 찾을 수 없습니다: {}. 임포트를 건너뜁니다.", CSV_PATH);
            return;
        }

        Set<String> existing = new HashSet<>();
        attractionRepository.findAll().forEach(a -> existing.add(a.getPlaceId()));

        int successCount = 0;
        int skippedCount = 0;
        int errorCount = 0;

        try (CSVReader reader = new CSVReaderBuilder(new FileReader(CSV_PATH))
                .withSkipLines(1)
                .build()) {

            String[] row;
            while ((row = reader.readNext()) != null) {
                try {
                    Attraction attraction = mapRowToAttraction(row);

                    if (attraction.getPlaceId() == null || !existing.add(attraction.getPlaceId())) {
                        skippedCount++;   // 이미 있거나 CSV 안에서 중복
                        continue;
                    }

                    attractionRepository.save(attraction);
                    successCount++;

                } catch (Exception e) {
                    errorCount++;
                    log.error("행 처리 중 오류 발생: {}", String.join(",", row), e);
                }
            }
        }

        if (successCount > 0 || errorCount > 0) {
            log.info("[관광지 임포트] 새로 추가 {}건 / 이미 있음 {}건 / 오류 {}건", successCount, skippedCount, errorCount);
        } else {
            log.info("[관광지 임포트] 새 관광지 없음 (DB {}건)", existing.size());
        }
    }

    private Attraction mapRowToAttraction(String[] row) {
        // CSV 컬럼 순서: ward, place_id, name, address,
        //                latitude, longitude, rating, review_cnt, types

        double lat = Double.parseDouble(row[4]);
        double lng = Double.parseDouble(row[5]);
        Point location = GEOMETRY_FACTORY.createPoint(new Coordinate(lng, lat));

        return Attraction.builder()
                .ward(row[0])
                .placeId(row[1])
                .name(row[2])
                .address(row[3])
                .rating(parseDoubleOrNull(row[6]))
                .reviewCnt(parseIntOrNull(row[7]))
                .types(row[8])
                .location(location)
                .build();
    }

    private Double parseDoubleOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseIntOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return (int) Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}