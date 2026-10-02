package com.example.japantravelplanner.itinerary.travel;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Routes API로 받은 구간 이동 시간 캐시 (같은 두 장소 사이는 다시 묻지 않음) */
@Entity
@Table(name = "travel_time_cache",
        uniqueConstraints = @UniqueConstraint(columnNames = {"origin_key", "destination_key", "mode"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelTimeCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "origin_key", nullable = false, length = 30)
    private String originKey;

    @Column(name = "destination_key", nullable = false, length = 30)
    private String destinationKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LegMode mode;

    @Column(nullable = false)
    private int minutes;

    @Column(name = "distance_meters", nullable = false)
    private int distanceMeters;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public TravelTimeCache(String originKey, String destinationKey, LegMode mode, int minutes, int distanceMeters) {
        this.originKey = originKey;
        this.destinationKey = destinationKey;
        this.mode = mode;
        this.minutes = minutes;
        this.distanceMeters = distanceMeters;
        this.createdAt = LocalDateTime.now();
    }
}