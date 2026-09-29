package com.example.japantravelplanner.trip.dto;

import com.example.japantravelplanner.trip.entity.Interest;
import com.example.japantravelplanner.trip.entity.TransportMode;
import com.example.japantravelplanner.trip.entity.TravelPace;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

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

    @AssertTrue(message = "종료일은 시작일과 같거나 이후여야 합니다.")
    public boolean isDateRangeValid() {
        if (startDate == null || endDate == null) {
            return true;   // 비어 있는 경우는 @NotNull이 따로 알려줌
        }
        return !endDate.isBefore(startDate);
    }

    @AssertTrue(message = "여행 기간은 최대 7일입니다.")
    public boolean isWithinMaxDays() {
        if (startDate == null || endDate == null) {
            return true;
        }
        return ChronoUnit.DAYS.between(startDate, endDate) + 1 <= MAX_TRIP_DAYS;
    }
}