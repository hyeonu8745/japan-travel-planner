package com.example.japantravelplanner.restaurant.entity;

import jakarta.persistence.*;
import com.example.japantravelplanner.restaurant.util.BudgetParser;
import com.example.japantravelplanner.restaurant.util.BudgetRange;
import lombok.*;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;

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

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}