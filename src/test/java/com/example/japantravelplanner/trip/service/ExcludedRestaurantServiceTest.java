package com.example.japantravelplanner.trip.service;

import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.restaurant.repository.RestaurantRepository;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.repository.TripRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExcludedRestaurantServiceTest {

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
    private final ExcludedRestaurantService service = new ExcludedRestaurantService(tripRepository, restaurantRepository);
    private final Trip trip = Trip.builder().id(9L).build();

    @Test
    void 바꾼_식당_목록을_한국어_이름과_장르로_보여주고_풀_수_있다() {
        trip.excludeRestaurant(21L);
        when(tripRepository.findByIdAndUserId(9L, 1L)).thenReturn(Optional.of(trip));
        Restaurant ramen = Restaurant.builder().id(21L).name("麺屋").genre("ラーメン").build();
        when(restaurantRepository.findAllById(any())).thenReturn(List.of(ramen));

        assertThat(service.list(1L, 9L)).extracting(ExcludedRestaurantService.ExcludedRestaurantDto::restaurantId)
                .containsExactly(21L);

        service.allow(1L, 9L, 21L);
        assertThat(trip.excludedRestaurants()).isEmpty();
        assertThatThrownBy(() -> service.allow(1L, 9L, 21L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.list(2L, 9L)).isInstanceOf(NotFoundException.class);
    }
}
