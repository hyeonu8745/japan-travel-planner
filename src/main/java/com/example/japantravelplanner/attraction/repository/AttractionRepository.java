package com.example.japantravelplanner.attraction.repository;

import com.example.japantravelplanner.attraction.entity.Attraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttractionRepository extends JpaRepository<Attraction, Long> {

    Optional<Attraction> findByPlaceId(String placeId);

    boolean existsByPlaceId(String placeId);

    List<Attraction> findByWard(String ward);

    List<Attraction> findByTypesContaining(String type);

    // Restaurant와 동일한 패턴: 반경검색 + 거리순 정렬
    @Query(value = """
            SELECT * FROM attractions a
            WHERE ST_DWithin(
                a.location::geography,
                ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                :radiusMeters
            )
            ORDER BY ST_Distance(
                a.location::geography,
                ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
            )
            """, nativeQuery = true)
    List<Attraction> findWithinRadius(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusMeters") double radiusMeters
    );
}