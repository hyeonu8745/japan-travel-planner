package com.example.japantravelplanner.trip.service;

import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.repository.TripRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 11-08 리팩터링: 서비스 15곳에 복사돼 있던 "내 여행 찾기" */
class TripAccessTest {

    private final TripRepository repo = mock(TripRepository.class);

    @Test
    void 내_여행이면_돌려준다() {
        Trip trip = Trip.builder().title("도쿄 3박 4일").build();
        when(repo.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(trip));

        assertThat(TripAccess.owned(repo, 1L, 7L)).isSameAs(trip);
    }

    @Test
    void 다른_사람_여행이거나_없으면_404() {
        when(repo.findByIdAndUserId(7L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> TripAccess.owned(repo, 2L, 7L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("여행을 찾을 수 없습니다.");
    }
}
