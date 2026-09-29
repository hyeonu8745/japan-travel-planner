package com.example.japantravelplanner.trip.service;

import com.example.japantravelplanner.trip.dto.TripRequestDto;
import com.example.japantravelplanner.trip.dto.TripResponseDto;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.user.entity.User;
import com.example.japantravelplanner.trip.repository.TripRepository;
import com.example.japantravelplanner.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.example.japantravelplanner.global.exception.NotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

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
                .build();

        Trip savedTrip = tripRepository.save(trip);
        return TripResponseDto.from(savedTrip);
    }

    public List<TripResponseDto> getMyTrips(Long userId) {
        return tripRepository.findByUserId(userId).stream()
                .map(TripResponseDto::from)
                .collect(Collectors.toList());
    }

    public TripResponseDto getTrip(Long userId, Long tripId) {
        Trip trip = tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new NotFoundException("여행을 찾을 수 없습니다."));
        return TripResponseDto.from(trip);
    }
}