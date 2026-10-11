package com.example.japantravelplanner.restaurant.repository;

import com.example.japantravelplanner.restaurant.entity.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long>, RestaurantSearchRepository {

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
    // 리뷰 분석 표본(개발용): Google에서 찾았거나 아직 확인 전인 식당을 리뷰 많은 순으로 (한국인 리뷰가 있을 가능성이 큼)
    @Query("select r from Restaurant r where r.googleMatchStatus is null or r.googleMatchStatus = :matched order by r.reviewCnt desc nulls last")
    List<Restaurant> findReviewSample(@Param("matched") com.example.japantravelplanner.restaurant.entity.GoogleMatchStatus matched,
                                      org.springframework.data.domain.Pageable pageable);

    // 예산 파싱이 이미 실행됐는지 확인 (마이그레이션 Runner 중복 실행 방지용)
    boolean existsByBudgetDinnerMinIsNotNull();

    /** 한국어 이름 저장 (이미 있으면 덮어쓰지 않는다) */
    @Modifying
    @Query("update Restaurant r set r.nameKo = :nameKo where r.id = :id and r.nameKo is null")
    int saveNameKo(@Param("id") Long id, @Param("nameKo") String nameKo);
}
