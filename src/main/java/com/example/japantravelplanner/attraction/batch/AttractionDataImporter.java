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
        long existingCount = attractionRepository.count();
        if (existingCount > 0) {
            log.info("이미 {}건의 관광지 데이터가 존재합니다. 임포트를 건너뜁니다.", existingCount);
            return;
        }

        if (!Paths.get(CSV_PATH).toFile().exists()) {
            log.warn("CSV 파일을 찾을 수 없습니다: {}. 임포트를 건너뜁니다.", CSV_PATH);
            return;
        }

        log.info("관광지 데이터 임포트를 시작합니다: {}", CSV_PATH);

        int successCount = 0;
        int errorCount = 0;

        try (CSVReader reader = new CSVReaderBuilder(new FileReader(CSV_PATH))
                .withSkipLines(1)
                .build()) {

            String[] row;
            while ((row = reader.readNext()) != null) {
                try {
                    Attraction attraction = mapRowToAttraction(row);

                    if (attractionRepository.existsByPlaceId(attraction.getPlaceId())) {
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

        log.info("=== 관광지 임포트 완료 ===");
        log.info("성공: {}건 / 오류: {}건", successCount, errorCount);
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