package com.example.japantravelplanner.restaurant.entity;

import jakarta.persistence.*;
import com.example.japantravelplanner.restaurant.util.BudgetParser;
import com.example.japantravelplanner.restaurant.util.BudgetRange;
import com.example.japantravelplanner.trip.region.City;
import com.example.japantravelplanner.trip.region.RegionConverters;
import lombok.*;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;

/**
 * 식당 ({@code restaurants}): 타베로그 데이터(이름·장르·평점·리뷰 수·예산 글자·휴일·주소·가까운 역)와 좌표.
 * 확장 데이터(V4)부터 도시·출처(캐글/타베로그/체인점)·휴일 정보·가게 특징, 체인점은 브랜드(ChainBrand)를 가리킨다.
 * 예산 글자는 숫자 범위(budgetDinnerMin·Max 등)로도 둔다. Google 과 맞춰 본 결과는 place ID 와 상태만 저장한다 (약관).
 */
@Entity
@Table(name = "restaurants")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 타베로그 원본 store_id (중복 적재 방지용)
    @Column(name = "store_id", unique = true)
    private String storeId;

    @Column(nullable = false)
    private String name;

    @Column(name = "nearest_station")
    private String nearestStation;

    @Column(name = "nearest_distance")
    private Integer nearestDistance; // 미터 단위

    @Column(name = "genre")
    private String genre; // 일단 "、" 구분된 원본 문자열 그대로

    @Column(name = "rating_val")
    private Double ratingVal;

    @Column(name = "review_cnt")
    private Integer reviewCnt;

    @Column(name = "save_cnt")
    private Integer saveCnt;

    @Column(name = "budget_dinner")
    private String budgetDinner; // 원본은 "￥3,000～￥3,999" 형태 텍스트

    @Column(name = "budget_lunch")
    private String budgetLunch;

    // 예산 텍스트를 파싱한 숫자 값 (엔 단위, null = 정보 없음 또는 상한 없음)
    @Column(name = "budget_dinner_min")
    private Integer budgetDinnerMin;

    @Column(name = "budget_dinner_max")
    private Integer budgetDinnerMax;

    @Column(name = "budget_lunch_min")
    private Integer budgetLunchMin;

    @Column(name = "budget_lunch_max")
    private Integer budgetLunchMax;

    @Column(name = "holiday")
    private String holiday;

    @Column(name = "address", length = 500)
    private String address;

    private String prefecture;

    @Column(name = "municipalities_1")
    private String municipalities1;

    @Column(name = "municipalities_2")
    private String municipalities2;

    @Column(name = "municipalities_3")
    private String municipalities3;

    // ⭐ PostGIS 공간 데이터 타입 (위경도)
    @Column(columnDefinition = "geometry(Point,4326)")
    private Point location;

    // Google 장소 매칭 결과 (일정을 만들 때 고른 식당만 확인한다. null = 아직 확인 안 함).
    // 약관상 Google 내용은 place ID만 저장할 수 있어서, 이름·주소·평점은 저장하지 않는다
    @Column(name = "google_place_id")
    private String googlePlaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "google_match_status", length = 20)
    private GoogleMatchStatus googleMatchStatus;

    @Column(name = "google_matched_at")
    private LocalDateTime googleMatchedAt;

    // 한국어 이름 (일정에 처음 쓰일 때 AI로 한 번 만들어 저장). null = 아직 없음 → 화면은 원래 이름
    @Column(name = "name_ko", length = 200)
    private String nameKo;

    // ---- 확장 데이터 (V4, 11-16). 도쿄 예전 행은 source=KAGGLE·city=TOKYO 만 있고 나머지는 비어 있다 ----

    // 도시 (DB에는 TOKYO 같은 이름 문자열)
    @Convert(converter = RegionConverters.CityConverter.class)
    @Column(name = "city", length = 20)
    private City city;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 10)
    private RestaurantSource source;

    // 영업시간 원문 (타베로그 글자 그대로)
    @Column(name = "business_hours", columnDefinition = "text")
    private String businessHours;

    // 좌표 정확도: banchi(번지) / town(동네 중심). town 이면 거리 계산이 조금 어긋날 수 있다
    @Column(name = "geo_level", length = 10)
    private String geoLevel;

    // 좌표가 주소와 맞지 않아 보이는 식당 (데이터 정리 때 표시)
    @Column(name = "location_suspect")
    private Boolean locationSuspect;

    // 정기 휴일 요일 (쉼표 구분, 예: TUE,WED). holiday 글자를 정리한 값
    @Column(name = "closed_days", length = 40)
    private String closedDays;

    // 부정기 휴일도 있음 (그날 쉬는지 확실하지 않음)
    @Column(name = "closed_irregular")
    private Boolean closedIrregular;

    // 연중무휴
    @Column(name = "no_regular_holiday")
    private Boolean noRegularHoliday;

    // 공휴일에 쉼
    @Column(name = "closed_on_holidays")
    private Boolean closedOnHolidays;

    // 완전 예약제 (예약 없이 못 감)
    @Column(name = "reservation_only")
    private Boolean reservationOnly;

    // 전석 흡연
    @Column(name = "smoking_all_seats")
    private Boolean smokingAllSeats;

    // 한국어 메뉴 있음
    @Column(name = "korean_menu")
    private Boolean koreanMenu;

    // 타베로그 백명점
    @Column(name = "hyakumeiten")
    private Boolean hyakumeiten;

    // 타베로그 어워드 메달 (Gold/Silver/Bronze)
    @Column(name = "award_medal", length = 10)
    private String awardMedal;

    // 서서 마시는 가게 (다치노미)
    @Column(name = "standing_bar")
    private Boolean standingBar;

    // 체인점 브랜드 (source=CHAIN 일 때만). 한국어 이름·종류·장르·예산은 브랜드 표에 (V5).
    // 바로 읽는다(EAGER): 식당 후보를 고르는 계산이 트랜잭션 밖에서도 장르·예산을 봐야 한다 (브랜드는 86개뿐)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "brand_id")
    private ChainBrand brand;

    // 체인점 영업시간 (OSM opening_hours 형식)
    @Column(name = "opening_hours", length = 200)
    private String openingHours;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 원본 예산 텍스트(budgetDinner/budgetLunch)를 파싱해 숫자 컬럼을 채운다. */
    public void parseBudget() {
        BudgetRange dinner = BudgetParser.parse(this.budgetDinner);
        BudgetRange lunch = BudgetParser.parse(this.budgetLunch);
        this.budgetDinnerMin = dinner.min();
        this.budgetDinnerMax = dinner.max();
        this.budgetLunchMin = lunch.min();
        this.budgetLunchMax = lunch.max();
    }

    /** 체인점인지 (브랜드가 붙은 지점) */
    public boolean isChain() {
        return brand != null;
    }

    /** 장르 (콤마 구분). 타베로그 장르가 없는 체인점은 브랜드 장르를 같은 이름표로 (ChainGenres) */
    public String genreOrBrand() {
        if (genre != null && !genre.isBlank()) return genre;
        return brand == null ? genre : brand.tabelogGenre();
    }

    /** 점심 1인 예산 하한 (체인점은 브랜드 예산) */
    public Integer lunchMin() {
        return budgetLunchMin != null || brand == null ? budgetLunchMin : brand.getBudgetMin();
    }

    /** 점심 1인 예산 상한 (체인점은 브랜드 예산) */
    public Integer lunchMax() {
        return budgetLunchMax != null || brand == null ? budgetLunchMax : brand.getBudgetMax();
    }

    /** 저녁 1인 예산 하한 (체인점은 브랜드 예산) */
    public Integer dinnerMin() {
        return budgetDinnerMin != null || brand == null ? budgetDinnerMin : brand.getBudgetMin();
    }

    /** 저녁 1인 예산 상한 (체인점은 브랜드 예산) */
    public Integer dinnerMax() {
        return budgetDinnerMax != null || brand == null ? budgetDinnerMax : brand.getBudgetMax();
    }

    /** Google에서 찾지 못했거나 폐업이라 끼니 후보에서 빼야 하는 식당 (아직 확인 안 한 식당은 통과) */
    public boolean isExcludedByGoogleMatch() {
        return googleMatchStatus != null && googleMatchStatus.excludesFromMeals();
    }

    /** 매칭 결과 기록. NOT_FOUND면 placeId는 null */
    public void recordGoogleMatch(GoogleMatchStatus status, String placeId, LocalDateTime matchedAt) {
        this.googleMatchStatus = status;
        this.googlePlaceId = placeId;
        this.googleMatchedAt = matchedAt;
    }

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
