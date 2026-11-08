package com.example.japantravelplanner.trip.service;

import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.trip.dto.TripRequestDto;
import com.example.japantravelplanner.trip.dto.TripResponseDto;
import com.example.japantravelplanner.trip.entity.Airport;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.repository.TripRepository;
import com.example.japantravelplanner.user.entity.User;
import com.example.japantravelplanner.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripService {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;

    @Transactional
    public TripResponseDto createTrip(Long userId, TripRequestDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));

        Trip trip = Trip.builder()
                .user(user)
                .title(defaultTitle(request.getStartDate(), request.getEndDate()))
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
        if (startDate == null || endDate == null) {
            return "도쿄 여행";
        }
        long nights = ChronoUnit.DAYS.between(startDate, endDate);
        if (nights <= 0) {
            return "도쿄 당일치기";
        }
        return "도쿄 " + nights + "박 " + (nights + 1) + "일";
    }

    public List<TripResponseDto> getMyTrips(Long userId) {
        return tripRepository.findByUserId(userId).stream()
                .map(TripResponseDto::from)
                .collect(Collectors.toList());
    }

    public TripResponseDto getTrip(Long userId, Long tripId) {
        Trip trip = TripAccess.owned(tripRepository, userId, tripId);
        return TripResponseDto.from(trip);
    }
}