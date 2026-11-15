package com.example.japantravelplanner.trip.dto;

import com.example.japantravelplanner.trip.entity.Interest;
import com.example.japantravelplanner.trip.entity.Trip;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 11-15 open-in-view 끔: 응답에 엔티티의 목록을 그대로 넣으면 트랜잭션 밖 JSON 변환에서 지연 로딩 오류(내 여행 목록 500) */
class TripResponseDtoTest {

    @Test
    void 관심사는_엔티티_목록을_복사해서_담는다() {
        List<Interest> interests = new ArrayList<>(List.of(Interest.FOOD, Interest.SHOPPING));
        Trip trip = Trip.builder().id(13L).startDate(LocalDate.of(2026, 12, 1)).endDate(LocalDate.of(2026, 12, 3))
                .interests(interests).build();

        TripResponseDto dto = TripResponseDto.from(trip);

        assertThat(dto.getInterests()).containsExactly(Interest.FOOD, Interest.SHOPPING).isNotSameAs(interests);
        interests.clear();
        assertThat(dto.getInterests()).hasSize(2);
    }
}
