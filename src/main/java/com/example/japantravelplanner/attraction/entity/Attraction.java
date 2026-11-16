package com.example.japantravelplanner.attraction.entity;

import jakarta.persistence.*;
import com.example.japantravelplanner.trip.region.City;
import com.example.japantravelplanner.trip.region.RegionConverters;
import lombok.*;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;

/**
 * 관광지 ({@code attractions}). Google Places 로 수집한 이름·주소·평점·리뷰 수·종류(types)와 좌표.
 * Google 의 사진·소개·영업시간은 저장하지 않고 상세를 볼 때 받아온다. 한국어 이름(nameKo)은 처음 일정에 쓰일 때 AI 로 만든다.
 * 확장 데이터(V4)는 OpenStreetMap·위키데이터에서 왔다: 종류(kind)·인기도(popularity)·야경·꼭 가는 곳·공식 운영 정보·위키미디어 사진.
 */
@Entity
@Table(name = "attractions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Google Places의 고유 ID (중복 적재 방지용)
    @Column(name = "place_id", unique = true)
    private String placeId;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String address;

    private String ward; // 千代田区, 港区 등 (municipalities_1과 매칭)

    private Double rating;

    @Column(name = "review_cnt")
    private Integer reviewCnt;

    @Column(length = 500)
    private String types; // "park,tourist_attraction" 등 콤마 구분

    @Column(columnDefinition = "geometry(Point,4326)")
    private Point location;

    // 한국어 이름 (일정에 처음 쓰일 때 AI로 한 번 만들어 저장). null = 아직 없음 → 화면은 원래 이름.
    // Google 한국어 이름은 약관상 저장할 수 없어서 쓰지 않는다
    @Column(name = "name_ko", length = 200)
    private String nameKo;

    // ---- 확장 데이터 (V4, 11-16: OpenStreetMap + 위키데이터). 도쿄 예전 행은 city=TOKYO 만 있다 ----

    // OSM 아이디 (예: way/336641107). 확장 데이터 중복 적재 방지용
    @Column(name = "osm_id", length = 40, unique = true)
    private String osmId;

    @Convert(converter = RegionConverters.CityConverter.class)
    @Column(name = "city", length = 20)
    private City city;

    // 데이터를 받은 지역 이름들 (쉼표 구분)
    @Column(name = "areas", length = 200)
    private String areas;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 20)
    private AttractionKind kind;

    @Column(name = "name_en", length = 200)
    private String nameEn;

    // 위키데이터 아이디 (예: Q221716). 인기도와 한국어 이름의 근거
    @Column(name = "wikidata", length = 20)
    private String wikidata;

    // 인기도 = 위키 문서가 있는 언어 수 (sitelinks). 클수록 유명하다
    @Column(name = "popularity")
    private Integer popularity;

    // OSM 영업시간 (opening_hours 형식)
    @Column(name = "opening_hours", length = 300)
    private String openingHours;

    @Column(name = "website", length = 300)
    private String website;

    // 입장료 표시 (OSM: yes/no 또는 금액 글자)
    @Column(name = "fee", length = 30)
    private String fee;

    // 역사 배경 안내 (민감하지만 대표 관광지라 빼지 않은 곳, 예: 오사카성)
    @Column(name = "history_note", length = 300)
    private String historyNote;

    // 야경 명소
    @Column(name = "night_view")
    private Boolean nightView;

    // 그 도시에서 꼭 가는 관광지
    @Column(name = "must_see")
    private Boolean mustSee;

    @Embedded
    private NightInfo nightInfo;

    @Embedded
    private OfficialInfo officialInfo;

    @Embedded
    private AttractionPhoto photo;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 처음 저장할 때 만든·고친 시각 */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /** 고칠 때마다 고친 시각 */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}