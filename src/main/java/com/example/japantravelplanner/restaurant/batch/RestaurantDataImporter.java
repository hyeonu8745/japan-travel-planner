package com.example.japantravelplanner.restaurant.batch;

import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.restaurant.repository.RestaurantRepository;
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

/**
 * 서버 시작 때 타베로그 식당 CSV(도쿄)를 DB 에 넣는다. 이미 데이터가 있으면 건너뛴다. 예산 글자는 넣을 때 숫자로 바꾼다.
 * 11월 확장 때 새 데이터(clean_restaurants_*.json) 가져오기로 바꾼다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class RestaurantDataImporter implements CommandLineRunner {

    private final RestaurantRepository restaurantRepository;

    // PostGIS SRID 4326 (WGS84, 일반 GPS 좌표계)
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    private static final String CSV_PATH = "data/processed/restaurants_tokyo_filtered_geocoded.csv";

    /** 서버 시작 때 식당이 하나도 없으면 CSV 를 읽어 넣는다 (중복 store_id 는 건너뛴다) */
    @Override
    public void run(String... args) throws Exception {
        long existingCount = restaurantRepository.count();
        if (existingCount > 0) {
            log.info("이미 {}건의 레스토랑 데이터가 존재합니다. 임포트를 건너뜁니다.", existingCount);
            return;
        }

        if (!Paths.get(CSV_PATH).toFile().exists()) {
            log.warn("CSV 파일을 찾을 수 없습니다: {}. 임포트를 건너뜁니다.", CSV_PATH);
            return;
        }

        log.info("타베로그 데이터 임포트를 시작합니다: {}", CSV_PATH);

        int successCount = 0;
        int skipCount = 0;
        int noLocationCount = 0;
        int errorCount = 0;

        try (CSVReader reader = new CSVReaderBuilder(new FileReader(CSV_PATH))
                .withSkipLines(1)
                .build()) {

            String[] row;
            while ((row = reader.readNext()) != null) {
                try {
                    Restaurant restaurant = mapRowToRestaurant(row);

                    if (restaurantRepository.existsByStoreId(restaurant.getStoreId())) {
                        skipCount++;
                        continue;
                    }

                    if (restaurant.getLocation() == null) {
                        noLocationCount++;
                    }

                    restaurantRepository.save(restaurant);
                    successCount++;

                    if (successCount % 1000 == 0) {
                        log.info("진행 상황: {}건 저장 완료", successCount);
                    }

                } catch (Exception e) {
                    errorCount++;
                    log.error("행 처리 중 오류 발생: {}", String.join(",", row), e);
                }
            }
        }

        log.info("=== 임포트 완료 ===");
        log.info("성공: {}건 / 중복 스킵: {}건 / 위경도 없음: {}건 / 오류: {}건",
                successCount, skipCount, noLocationCount, errorCount);
    }

    /** CSV 한 줄 → 식당 (위도·경도가 있으면 좌표, 예산 글자는 숫자 범위로도) */
    private Restaurant mapRowToRestaurant(String[] row) {
        Point location = null;
        String latStr = row[16];
        String lngStr = row[17];

        if (isNotBlank(latStr) && isNotBlank(lngStr)) {
            double lat = Double.parseDouble(latStr);
            double lng = Double.parseDouble(lngStr);
            location = GEOMETRY_FACTORY.createPoint(new Coordinate(lng, lat));
        }

        Restaurant restaurant = Restaurant.builder()
                .storeId(row[0])
                .name(row[1])
                .nearestStation(nullIfBlank(row[2]))
                .nearestDistance(parseIntOrNull(row[3]))
                .genre(nullIfBlank(row[4]))
                .ratingVal(parseDoubleOrNull(row[5]))
                .reviewCnt(parseIntOrNull(row[6]))
                .saveCnt(parseIntOrNull(row[7]))
                .budgetDinner(nullIfBlank(row[8]))
                .budgetLunch(nullIfBlank(row[9]))
                .holiday(nullIfBlank(row[10]))
                .address(nullIfBlank(row[11]))
                .prefecture(nullIfBlank(row[12]))
                .municipalities1(nullIfBlank(row[13]))
                .municipalities2(nullIfBlank(row[14]))
                .municipalities3(nullIfBlank(row[15]))
                .location(location)
                .build();
        // 예산 텍스트 → 숫자 min/max 변환
        restaurant.parseBudget();
        return restaurant;
    }

    /** 비어 있지 않은지 */
    private boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** 비어 있으면 null */
    private String nullIfBlank(String s) {
        return isNotBlank(s) ? s : null;
    }

    /** 숫자 글자 → 정수 ("123.0"도 읽는다, 비었거나 틀리면 null) */
    private Integer parseIntOrNull(String s) {
        if (!isNotBlank(s)) return null;
        try {
            return (int) Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 숫자 글자 → 실수 (비었거나 틀리면 null) */
    private Double parseDoubleOrNull(String s) {
        if (!isNotBlank(s)) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}