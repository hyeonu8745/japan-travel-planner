package com.example.japantravelplanner.trip.service;

import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.itinerary.naming.GenreLabels;
import com.example.japantravelplanner.restaurant.repository.RestaurantRepository;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 사용자가 이 여행에서 바꾼 식당 목록 (10-12). 다시 추천하지 않는 곳을 보여주고, "다시 추천해도 돼요"로 풀 수 있게
 */
@Service
@RequiredArgsConstructor
public class ExcludedRestaurantService {

    private final TripRepository tripRepository;
    private final RestaurantRepository restaurantRepository;

    /** @param genreLabels 장르 한국어 이름 */
    public record ExcludedRestaurantDto(Long restaurantId, String name, String nameKo, List<String> genreLabels) {
    }

    @Transactional(readOnly = true)
    public List<ExcludedRestaurantDto> list(Long userId, Long tripId) {
        Trip trip = TripAccess.owned(tripRepository, userId, tripId);
        return restaurantRepository.findAllById(trip.excludedRestaurants()).stream()
                .map(r -> new ExcludedRestaurantDto(r.getId(), r.getName(), r.getNameKo(), GenreLabels.of(r.getGenre())))
                .toList();
    }

    /** 빼기 해제 (목록에 없으면 404) */
    @Transactional
    public void allow(Long userId, Long tripId, Long restaurantId) {
        Trip trip = TripAccess.owned(tripRepository, userId, tripId);
        if (!trip.allowRestaurant(restaurantId)) {
            throw new NotFoundException("다시 추천하지 않는 식당 목록에 없어요.");
        }
    }

}
