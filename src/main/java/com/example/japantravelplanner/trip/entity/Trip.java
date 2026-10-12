package com.example.japantravelplanner.trip.entity;

import com.example.japantravelplanner.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Point;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    /**
     * 사용자가 이 여행에서 다른 곳으로 바꾼 식당 (10-12). 교체 후보·다시 만드는 일정에 다시 넣지 않는다.
     * 리뷰가 마음에 안 들어 바꾼 식당이 다음 후보로 또 나오지 않게 (Google 리뷰가 아니라 사용자 선택이라 저장해도 된다)
     */
    @ElementCollection
    @CollectionTable(name = "trip_excluded_restaurants", joinColumns = @JoinColumn(name = "trip_id"))
    @Column(name = "restaurant_id")
    @Builder.Default
    private Set<Long> excludedRestaurantIds = new HashSet<>();

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

    /**
     * 일정을 고쳤을 때의 상태. 확인 대기(완성 전)면 그대로 두고, 그 밖에는 EDITING (10-12 사용자 결정:
     * 완성 전에 고쳐도 확인 대기 유지 → 다 고친 뒤 [완성])
     */
    public void markEdited() {
        if (status != TripStatus.AWAITING_CONFIRMATION) {
            status = TripStatus.EDITING;
        }
    }

    public void excludeRestaurant(Long restaurantId) {
        if (restaurantId == null) return;
        if (excludedRestaurantIds == null) excludedRestaurantIds = new HashSet<>();
        excludedRestaurantIds.add(restaurantId);
    }

    /** 사용자가 "다시 추천해도 돼요"를 고르면 */
    public boolean allowRestaurant(Long restaurantId) {
        return excludedRestaurantIds != null && excludedRestaurantIds.remove(restaurantId);
    }

    public Set<Long> excludedRestaurants() {
        return excludedRestaurantIds == null ? Set.of() : excludedRestaurantIds;
    }

    public boolean isAwaitingConfirmation() {
        return status == TripStatus.AWAITING_CONFIRMATION;
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