package com.example.japantravelplanner.trip.entity;

import com.example.japantravelplanner.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Point;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "trips")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 이 여행을 만든 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 여행 제목 (생성 시 날짜로 자동 생성, 예: "도쿄 3박 4일")
    @Column(length = 100)
    private String title;

    private LocalDate startDate;
    private LocalDate endDate;

    // 인원 수 (1~20)
    private Integer travelers;

    // 전체 여행 예산 (원화, 인원 전체 합계 기준)
    private Integer budget;

    @Enumerated(EnumType.STRING)
    private TravelPace travelPace;

    @ElementCollection
    @CollectionTable(name = "trip_interests", joinColumns = @JoinColumn(name = "trip_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "interest")
    private List<Interest> interests;

    @Column(length = 500)
    private String allergies;

    @Column(length = 500)
    private String mobilityConstraints;

    @Enumerated(EnumType.STRING)
    private TransportMode transportMode; // PUBLIC_TRANSIT, RENTAL_CAR

    private String accommodationName;

    @Column(length = 500)
    private String accommodationAddress;

    @Column(columnDefinition = "geometry(Point,4326)")
    private Point accommodationLocation; // 일정 생성 시 Geocoding으로 채움

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TripStatus status = TripStatus.CONDITIONS_SET;

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