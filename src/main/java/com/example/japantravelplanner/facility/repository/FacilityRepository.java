package com.example.japantravelplanner.facility.repository;

import com.example.japantravelplanner.facility.entity.Facility;
import org.springframework.data.jpa.repository.JpaRepository;

/** 편의 시설 저장소. 가까운 곳 찾기는 숙소 주변 안내를 만들 때(8단계) 더한다 */
public interface FacilityRepository extends JpaRepository<Facility, Long> {

    /** 이미 들어간 OSM 아이디인지 (다시 가져올 때 중복 방지) */
    boolean existsByOsmId(String osmId);
}
