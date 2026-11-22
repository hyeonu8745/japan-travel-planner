package com.example.japantravelplanner.trip.dto;

import com.example.japantravelplanner.trip.entity.Airport;
import com.example.japantravelplanner.trip.entity.Interest;
import com.example.japantravelplanner.trip.entity.TransportMode;
import com.example.japantravelplanner.trip.entity.TravelPace;
import com.example.japantravelplanner.trip.region.City;
import com.example.japantravelplanner.trip.region.Region;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 여행 만들기·고치기 요청. 날짜는 최대 7일, 공항은 여행 권역의 공항만, 비행기 시각은 공항과 같이 (검증 어노테이션).
 */
@Getter
@Setter
public class TripRequestDto {

    private static final int MAX_TRIP_DAYS = 7;

    @NotNull(message = "여행 시작일을 입력해주세요.")
    @FutureOrPresent(message = "여행 시작일은 오늘 이후여야 합니다.")
    private LocalDate startDate;

    @NotNull(message = "여행 종료일을 입력해주세요.")
    private LocalDate endDate;

    @NotNull(message = "인원을 입력해주세요.")
    @Min(value = 1, message = "인원은 1명 이상이어야 합니다.")
    @Max(value = 20, message = "인원은 20명 이하여야 합니다.")
    private Integer travelers;

    @NotNull(message = "예산을 입력해주세요.")
    @PositiveOrZero(message = "예산은 0원 이상이어야 합니다.")
    private Integer budget;

    @NotNull(message = "여행 속도를 선택해주세요.")
    private TravelPace travelPace;

    private List<Interest> interests;

    @Size(max = 500, message = "알레르기 정보는 500자 이하여야 합니다.")
    private String allergies;

    @Size(max = 500, message = "이동 제약 정보는 500자 이하여야 합니다.")
    private String mobilityConstraints;

    @NotNull(message = "이동수단을 선택해주세요.")
    private TransportMode transportMode;

    @NotBlank(message = "숙소 이름을 입력해주세요.")
    @Size(max = 255, message = "숙소 이름은 255자 이하여야 합니다.")
    private String accommodationName;

    @NotBlank(message = "숙소 주소를 입력해주세요.")
    @Size(max = 500, message = "숙소 주소는 500자 이하여야 합니다.")
    private String accommodationAddress;

    // 공항 (모두 선택). 공항 코드 NRT(나리타) / HND(하네다), 시각은 "HH:mm" (비행기 도착·출발 시각)
    private String arrivalAirport;
    private LocalTime arrivalFlightTime;
    private String departureAirport;
    private LocalTime departureFlightTime;

    /**
     * 권역 (선택, 11-22). KANTO·KANSAI·CHUBU·HIROSHIMA·KYUSHU_NORTH·HOKKAIDO·OKINAWA.
     * 숙소 이름·주소로 도시를 알 수 없을 때 이 권역의 대표 도시로 시작한다. 숙소 도시와 다르면 400
     */
    private String region;

    /**
     * 당일치기로 다녀올 도시 (선택, 최대 3곳, 11-22). 예: ["KYOTO", "NARA"].
     * 숙소 도시에서 갈 수 있는 도시만 (GET /api/regions 의 dayTrips)
     */
    @Size(max = 3, message = "당일치기 도시는 3곳까지 고를 수 있어요.")
    private List<String> dayTripCities;

    // 공항은 아는 코드인지만 여기서 본다. 숙소 권역의 공항인지는 TripPlaces 가 본다 (11-22, 예전에는 NRT·HND만)
    @AssertTrue(message = "공항 코드를 확인해주세요. (예: NRT, HND, KIX, FUK)")
    public boolean isAirportKnown() {
        return Airport.isKnownOrBlank(arrivalAirport) && Airport.isKnownOrBlank(departureAirport);
    }

    /** 권역 이름이 맞는지 (비어 있으면 통과) */
    @AssertTrue(message = "권역을 확인해주세요. (KANTO, KANSAI, CHUBU, HIROSHIMA, KYUSHU_NORTH, HOKKAIDO, OKINAWA)")
    public boolean isRegionKnown() {
        if (isBlank(region)) return true;
        return java.util.Arrays.stream(Region.values()).anyMatch(r -> r.name().equalsIgnoreCase(region.strip()));
    }

    /** 당일치기 도시 이름이 맞는지 (비어 있으면 통과) */
    @AssertTrue(message = "당일치기 도시 이름을 확인해주세요. (예: KYOTO, NARA)")
    public boolean isDayTripCitiesKnown() {
        return dayTripCities == null || dayTripCities.stream().allMatch(c -> City.of(c).isPresent());
    }

    /** 비행기 시각을 넣었으면 그 공항도 골랐는지 */
    @AssertTrue(message = "비행기 시각을 입력했다면 공항도 골라주세요.")
    public boolean isAirportGivenWithTime() {
        return (arrivalFlightTime == null || !isBlank(arrivalAirport))
                && (departureFlightTime == null || !isBlank(departureAirport));
    }

    /** 비어 있거나 공백뿐인지 */
    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** 종료일이 시작일보다 앞이 아닌지 (비어 있으면 @NotNull 이 따로 알린다) */
    @AssertTrue(message = "종료일은 시작일과 같거나 이후여야 합니다.")
    public boolean isDateRangeValid() {
        if (startDate == null || endDate == null) {
            return true;   // 비어 있는 경우는 @NotNull이 따로 알려줌
        }
        return !endDate.isBefore(startDate);
    }

    /** 여행 기간이 최대 일수(7일) 안인지 */
    @AssertTrue(message = "여행 기간은 최대 7일입니다.")
    public boolean isWithinMaxDays() {
        if (startDate == null || endDate == null) {
            return true;
        }
        return ChronoUnit.DAYS.between(startDate, endDate) + 1 <= MAX_TRIP_DAYS;
    }
}