package com.example.japantravelplanner.trip.service;

import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.trip.dto.TripRequestDto;
import com.example.japantravelplanner.trip.dto.TripResponseDto;
import com.example.japantravelplanner.trip.entity.Airport;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.region.City;
import com.example.japantravelplanner.trip.repository.TripRepository;
import com.example.japantravelplanner.user.entity.User;
import com.example.japantravelplanner.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 여행 만들기·목록·조회. 제목은 날짜로 만든다("도쿄 3박 4일" 등).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripService {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;

    /** 여행 만들기: 제목은 날짜로, 공항 코드는 대문자로 저장, 상태는 CONDITIONS_SET */
    @Transactional
    public TripResponseDto createTrip(Long userId, TripRequestDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));

        // 숙소 이름·주소에 도시 이름이 있으면 그 도시로, 없으면 고른 권역의 대표 도시 (일정을 만들 때 숙소 좌표로 다시 정한다)
        TripPlaces places = TripPlaces.resolve(request, null);
        City city = places.city();
        Trip trip = Trip.builder()
                .user(user)
                .title(defaultTitle(request.getStartDate(), request.getEndDate(), city))
                .baseCity(city)
                .region(city.region())
                .dayTripCities(new java.util.ArrayList<>(places.dayTrips()))
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .travelers(request.getTravelers())
                .budget(request.getBudget())
                .travelPace(request.getTravelPace())
                .interests(request.getInterests())
                .allergies(request.getAllergies())
                .mobilityConstraints(request.getMobilityConstraints())
                .transportMode(request.getTransportMode())
                .accommodationName(request.getAccommodationName())
                .accommodationAddress(request.getAccommodationAddress())
                .arrivalAirport(Airport.normalize(request.getArrivalAirport()))
                .arrivalFlightTime(request.getArrivalFlightTime())
                .departureAirport(Airport.normalize(request.getDepartureAirport()))
                .departureFlightTime(request.getDepartureFlightTime())
                .build();

        Trip savedTrip = tripRepository.save(trip);
        return TripResponseDto.from(savedTrip);
    }

    /** 날짜로 기본 제목 생성: "도쿄 3박 4일", 같은 날이면 "도쿄 당일치기" */
    static String defaultTitle(LocalDate startDate, LocalDate endDate) {
        return defaultTitle(startDate, endDate, City.TOKYO);
    }

    /** 도시·날짜로 기본 제목: "오사카 3박 4일", 같은 날이면 "오사카 당일치기", 날짜가 없으면 "오사카 여행" */
    static String defaultTitle(LocalDate startDate, LocalDate endDate, City city) {
        String name = (city == null ? City.TOKYO : city).label();
        if (startDate == null || endDate == null) {
            return name + " 여행";
        }
        long nights = ChronoUnit.DAYS.between(startDate, endDate);
        if (nights <= 0) {
            return name + " 당일치기";
        }
        return name + " " + nights + "박 " + (nights + 1) + "일";
    }

    /** 내 여행 목록 */
    public List<TripResponseDto> getMyTrips(Long userId) {
        return tripRepository.findByUserId(userId).stream()
                .map(TripResponseDto::from)
                .collect(Collectors.toList());
    }

    /** 내 여행 하나 (남의 여행이면 404) */
    public TripResponseDto getTrip(Long userId, Long tripId) {
        Trip trip = TripAccess.owned(tripRepository, userId, tripId);
        return TripResponseDto.from(trip);
    }
}
