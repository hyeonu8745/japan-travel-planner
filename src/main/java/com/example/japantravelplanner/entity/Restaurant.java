package com.example.japantravelplanner.entity;

import jakarta.persistence.*;
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

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

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