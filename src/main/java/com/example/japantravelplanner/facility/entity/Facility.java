package com.example.japantravelplanner.facility.entity;

import com.example.japantravelplanner.trip.region.City;
import com.example.japantravelplanner.trip.region.RegionConverters;
import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Point;

/**
 * 편의 시설 ({@code facilities}, OpenStreetMap). 편의점·ATM·코인로커·환전소·관광 안내소·온천·화장실·엘리베이터 등.
 * 일정을 만들 때 숙소·장소에서 가장 가까운 곳을 찾아 안내한다. 화면에는 "© OpenStreetMap contributors" 표시가 필요하다.
 * 이름은 OSM 기본 이름(name)과 언어별 이름이 따로 있다. 종류마다 쓰는 칸이 다르다 (온천은 bathType, 출입구는 ref 등).
 */
@Entity
@Table(name = "facilities")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Facility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // OSM 아이디 (예: node/12735745101). 중복 적재 방지용
    @Column(name = "osm_id", nullable = false, length = 40, unique = true)
    private String osmId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private FacilityType type;

    @Convert(converter = RegionConverters.CityConverter.class)
    @Column(name = "city", nullable = false, length = 20)
    private City city;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "name_ja", length = 100)
    private String nameJa;

    @Column(name = "name_en", length = 100)
    private String nameEn;

    @Column(name = "name_ko", length = 100)
    private String nameKo;

    @Column(columnDefinition = "geometry(Point,4326)", nullable = false)
    private Point location;

    // 운영 회사 (예: 세븐일레븐 운영사, 지하철 회사)
    @Column(name = "operator", length = 100)
    private String operator;

    @Column(name = "opening_hours", length = 200)
    private String openingHours;

    // 요금 (yes/no 또는 금액 글자)
    @Column(name = "fee", length = 100)
    private String fee;

    // 휠체어 이용 (yes/no/limited)
    @Column(name = "wheelchair", length = 15)
    private String wheelchair;

    @Column(name = "brand", length = 60)
    private String brand;

    // 자전거 주차장 네트워크 등
    @Column(name = "network", length = 40)
    private String network;

    // 역 출입구 번호 (예: A3)
    @Column(name = "ref", length = 60)
    private String ref;

    // 층 (역 안 엘리베이터 등)
    @Column(name = "level", length = 60)
    private String level;

    // 주차 가능 대수
    @Column(name = "capacity", length = 20)
    private String capacity;

    // 주차장 형태 (surface, multi-storey 등)
    @Column(name = "parking", length = 20)
    private String parking;

    // 최대 주차 시간
    @Column(name = "maxstay", length = 40)
    private String maxstay;

    // 요금표 글자 (예: 200/30min)
    @Column(name = "charge", length = 200)
    private String charge;

    // 온천 종류 (onsen 온천 / sento 대중목욕탕 / foot_bath 족욕)
    @Column(name = "bath_type", length = 15)
    private String bathType;

    /** 화면에 보일 이름: 한국어 → 일본어 → 기본 → 영어 순서로 있는 것 */
    public String displayName() {
        if (nameKo != null && !nameKo.isBlank()) return nameKo;
        if (nameJa != null && !nameJa.isBlank()) return nameJa;
        if (name != null && !name.isBlank()) return name;
        return nameEn;
    }
}
