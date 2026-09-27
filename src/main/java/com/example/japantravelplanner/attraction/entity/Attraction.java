package com.example.japantravelplanner.attraction.entity;

import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;

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