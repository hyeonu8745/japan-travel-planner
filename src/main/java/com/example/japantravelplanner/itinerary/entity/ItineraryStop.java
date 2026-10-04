package com.example.japantravelplanner.itinerary.entity;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.itinerary.travel.LegMode;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/**
 * 하루 안의 항목 하나 (관광지, 점심, 저녁).
 * 장소 정보는 복사하지 않고 관광지·식당 테이블을 참조한다. 종류에 따라 둘 중 하나만 채워진다.
 */
@Entity
@Table(name = "itinerary_stops",
        indexes = @Index(name = "idx_itinerary_stops_day", columnList = "day_id, stop_order"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItineraryStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "day_id", nullable = false)
    private ItineraryDay day;

    // 하루 안에서의 순서 (1부터)
    @Column(name = "stop_order", nullable = false)
    private int stopOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_type", nullable = false, length = 20)
    private StopType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attraction_id")
    private Attraction attraction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    // 식사는 대기가 끝나고 식사를 시작하는 시각
    @Column(name = "arrival_time", nullable = false)
    private LocalTime arrivalTime;

    @Column(name = "departure_time", nullable = false)
    private LocalTime departureTime;

    @Column(name = "stay_minutes", nullable = false)
    private int stayMinutes;

    // 식사 시간대를 기다린 시간 ("여유 시간")
    @Column(name = "wait_minutes", nullable = false)
    private int waitMinutes;

    // 앞 장소에서 여기까지 오는 이동 (첫 항목은 숙소에서)
    @Enumerated(EnumType.STRING)
    @Column(name = "travel_mode", nullable = false, length = 10)
    private LegMode travelMode;

    @Column(name = "travel_minutes", nullable = false)
    private int travelMinutes;

    @Column(name = "travel_distance_meters", nullable = false)
    private int travelDistanceMeters;

    // true면 외부 조회 실패로 대신 계산한 값 (화면에 "예상치" 배지)
    @Column(name = "travel_estimated", nullable = false)
    private boolean travelEstimated;

    // 식당 전용: 예산 안에서 못 찾아 상한을 올려서 찾았는지, 앞 끼니와 같은 음식을 피하지 못했는지
    @Column(name = "budget_relaxed", nullable = false)
    private boolean budgetRelaxed;

    @Column(name = "genre_repeated", nullable = false)
    private boolean genreRepeated;

    @Builder
    private ItineraryStop(int stopOrder, StopType type, Attraction attraction, Restaurant restaurant,
                          LocalTime arrivalTime, LocalTime departureTime, int stayMinutes, int waitMinutes,
                          LegMode travelMode, int travelMinutes, int travelDistanceMeters, boolean travelEstimated,
                          boolean budgetRelaxed, boolean genreRepeated) {
        this.stopOrder = stopOrder;
        this.type = type;
        this.attraction = attraction;
        this.restaurant = restaurant;
        this.arrivalTime = arrivalTime;
        this.departureTime = departureTime;
        this.stayMinutes = stayMinutes;
        this.waitMinutes = waitMinutes;
        this.travelMode = travelMode;
        this.travelMinutes = travelMinutes;
        this.travelDistanceMeters = travelDistanceMeters;
        this.travelEstimated = travelEstimated;
        this.budgetRelaxed = budgetRelaxed;
        this.genreRepeated = genreRepeated;
    }

    // 같은 패키지의 ItineraryDay.addStop에서만 호출
    void assignTo(ItineraryDay day) {
        this.day = day;
    }
}