package com.example.japantravelplanner.repository;

import com.example.japantravelplanner.entity.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    // store_id로 조회 (중복 적재 방지용)
    Optional<Restaurant> findByStoreId(String storeId);

    // store_id 존재 여부 확인 (배치 적재 시 중복 체크용)
    boolean existsByStoreId(String storeId);

    // 특정 구(municipalities_1) 기준 검색
    List<Restaurant> findByMunicipalities1(String municipalities1);

    // 장르로 검색 (LIKE 검색, genre가 "、"로 구분된 문자열이라서)
    List<Restaurant> findByGenreContaining(String genre);

    // 평점 이상 필터링
    List<Restaurant> findByRatingValGreaterThanEqual(Double ratingVal);

    // ⭐ 반경 검색 (PostGIS 공간 쿼리) - 나중에 TSP/경로 최적화에 사용
    @Query(value = """
            SELECT * FROM restaurants r
            WHERE ST_DWithin(
                r.location::geography,
                ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                :radiusMeters
            )
            ORDER BY ST_Distance(
                r.location::geography,
                ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
            )
            """, nativeQuery = true)
    List<Restaurant> findWithinRadius(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusMeters") double radiusMeters
    );

    // 위경도(location)가 아직 없는 데이터 조회 (Geocoding 배치 처리용)
    List<Restaurant> findByLocationIsNull();
}