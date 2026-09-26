package com.example.japantravelplanner.entity;

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

    private LocalDate startDate;
    private LocalDate endDate;

    private String travelers; // "혼자", "커플", "가족(4인)" 등

    private Integer budget;

    private String travelPace; // "여유", "보통", "빡빡" 등

    @ElementCollection
    @CollectionTable(name = "trip_interests", joinColumns = @JoinColumn(name = "trip_id"))
    @Column(name = "interest")
    private List<String> interests; // ["현지음식", "전통명소", "야경"]

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
    private Point accommodationLocation; // 지금은 null, 나중에 채움

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