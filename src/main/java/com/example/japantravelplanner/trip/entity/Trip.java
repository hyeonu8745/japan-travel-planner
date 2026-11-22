package com.example.japantravelplanner.trip.entity;

import com.example.japantravelplanner.trip.region.City;
import com.example.japantravelplanner.trip.region.Region;
import com.example.japantravelplanner.trip.region.RegionConverters;
import com.example.japantravelplanner.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Point;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 여행 ({@code trips}): 사용자, 날짜, 권역·기준 도시, 인원, 예산, 속도, 관심사, 알레르기·이동 제약, 숙소(이름·주소·좌표),
 * 도착·출발 공항과 비행기 시각, 상태, 다시 추천하지 않는 식당 id. 숙소 좌표는 처음 일정을 만들 때 Google 로 찾아 저장한다.
 */
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

    /**
     * 권역과 숙소 도시 (확장 계획 0-16). 날짜마다 도는 도시는 그날 지역(TripZone)의 도시다.
     * 지금은 모두 수도권·도쿄. 예전 행(값 없음)도 도쿄로 읽는다.
     */
    @Convert(converter = RegionConverters.RegionConverter.class)
    @Column(length = 20)
    @Builder.Default
    private Region region = Region.KANTO;

    @Convert(converter = RegionConverters.CityConverter.class)
    @Column(name = "base_city", length = 20)
    @Builder.Default
    private City baseCity = City.TOKYO;

    /**
     * 숙소에서 당일치기로 다녀올 도시 (11-22 확장 5단계, 예: 오사카 숙소 + 교토·나라). 여행 조건에서 고르고,
     * 일정의 가운데 날(첫날·마지막 날 아님)에 하루씩 넣는다. 갈 수 있는 도시는 DayTripRoute. DB에는 "KYOTO,NARA"
     */
    @Convert(converter = RegionConverters.CityListConverter.class)
    @Column(name = "day_trip_cities", length = 200)
    @Builder.Default
    private java.util.List<City> dayTripCities = new java.util.ArrayList<>();

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
     * 사용자가 이 여행에서 다른 곳으로 바꾼 식당. 교체 후보·다시 만드는 일정에 다시 넣지 않는다.
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

    // 공항 (모두 선택). 공항은 코드 문자열(Airport: NRT, HND), 시각은 비행기 도착·출발 시각(현지)
    @Column(name = "arrival_airport", length = 8)
    private String arrivalAirport;

    @Column(name = "arrival_flight_time")
    private LocalTime arrivalFlightTime;

    @Column(name = "departure_airport", length = 8)
    private String departureAirport;

    @Column(name = "departure_flight_time")
    private LocalTime departureFlightTime;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TripStatus status = TripStatus.CONDITIONS_SET;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 일정을 고쳤을 때의 상태. 확인 대기(완성 전)면 그대로 두고, 그 밖에는 EDITING
     * (완성 전에 고쳐도 확인 대기를 유지해서, 다 고친 뒤 [완성]을 누르게 한다)
     */
    public void markEdited() {
        if (status != TripStatus.AWAITING_CONFIRMATION) {
            status = TripStatus.EDITING;
        }
    }

    /** 이 여행에서 다시 추천하지 않을 식당으로 넣는다 (사용자가 다른 곳으로 바꾼 식당) */
    public void excludeRestaurant(Long restaurantId) {
        if (restaurantId == null) return;
        if (excludedRestaurantIds == null) excludedRestaurantIds = new HashSet<>();
        excludedRestaurantIds.add(restaurantId);
    }

    /** 사용자가 "다시 추천해도 돼요"를 고르면 */
    public boolean allowRestaurant(Long restaurantId) {
        return excludedRestaurantIds != null && excludedRestaurantIds.remove(restaurantId);
    }

    /** 다시 추천하지 않을 식당 id (없으면 빈 집합) */
    public Set<Long> excludedRestaurants() {
        return excludedRestaurantIds == null ? Set.of() : excludedRestaurantIds;
    }

    /**
     * 기본 제목("도쿄 3박 4일", "도쿄 당일치기", "도쿄 여행")이면 앞의 도시 이름을 숙소 도시로 바꾼다.
     * 여행을 만들 때는 도시를 몰라서 도쿄로 지어 둔다 (11-18). 사용자가 바꾼 제목은 그대로
     */
    public void renameDefaultTitle(City city) {
        if (title == null || city == null) return;
        if (title.matches("도쿄 (여행|당일치기|\\d+박 \\d+일)")) {
            title = city.label() + title.substring("도쿄".length());
        }
    }

    /** 여행 권역 (값이 없던 예전 여행은 수도권) */
    public Region getRegion() {
        return region == null ? Region.KANTO : region;
    }

    /** 당일치기 도시 (없으면 빈 목록) */
    public java.util.List<City> getDayTripCities() {
        return dayTripCities == null ? java.util.List.of() : dayTripCities;
    }

    /** 숙소가 있는 기준 도시 (값이 없던 예전 여행은 도쿄) */
    public City getBaseCity() {
        return baseCity == null ? City.TOKYO : baseCity;
    }

    /** 비행기 도착 정보가 다 있을 때만 (공항 + 시각). 여행 권역의 공항이 아니면 없는 것으로 본다 */
    public java.util.Optional<Airport> arrivalAirportInfo() {
        return arrivalFlightTime == null ? java.util.Optional.empty()
                : Airport.of(arrivalAirport).filter(a -> a.region() == getRegion());
    }

    /** 비행기 출발 정보가 다 있을 때만 (공항 + 시각). 여행 권역의 공항이 아니면 없는 것으로 본다 */
    public java.util.Optional<Airport> departureAirportInfo() {
        return departureFlightTime == null ? java.util.Optional.empty()
                : Airport.of(departureAirport).filter(a -> a.region() == getRegion());
    }

    /** 일정을 만든 직후, [완성]을 누르기 전인지 */
    public boolean isAwaitingConfirmation() {
        return status == TripStatus.AWAITING_CONFIRMATION;
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