package com.example.japantravelplanner.service;

import com.example.japantravelplanner.dto.TripRequestDto;
import com.example.japantravelplanner.dto.TripResponseDto;
import com.example.japantravelplanner.entity.Trip;
import com.example.japantravelplanner.entity.User;
import com.example.japantravelplanner.repository.TripRepository;
import com.example.japantravelplanner.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;

    public TripResponseDto createTrip(Long userId, TripRequestDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

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

    public TripResponseDto getTrip(Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("여행을 찾을 수 없습니다."));
        return TripResponseDto.from(trip);
    }
}