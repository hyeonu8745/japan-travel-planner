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

    // 한국어 이름 (일정에 처음 쓰일 때 AI로 한 번 만들어 저장, 10-11). null = 아직 없음 → 화면은 원래 이름.
    // Google 한국어 이름은 약관상 저장할 수 없어서 쓰지 않는다
    @Column(name = "name_ko", length = 200)
    private String nameKo;

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