package com.example.japantravelplanner.itinerary.candidate;

import com.example.japantravelplanner.attraction.entity.Attraction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 비를 피할 수 있는 곳인지 (10-16) */
class ShelterTest {

    private static Shelter of(String types) {
        return Shelter.of(Attraction.builder().types(types).build());
    }

    @Test
    void 박물관_백화점_수족관_전망대는_실내() {
        assertThat(of("museum,tourist_attraction")).isEqualTo(Shelter.INDOOR);
        assertThat(of("department_store,store")).isEqualTo(Shelter.INDOOR);
        assertThat(of("aquarium")).isEqualTo(Shelter.INDOOR);
        assertThat(of("observation_deck,tourist_attraction")).isEqualTo(Shelter.INDOOR);
    }

    @Test
    void 공원_신사_절_동물원은_실외이고_실내_종류가_섞여도_공원이면_실외() {
        assertThat(of("park,garden")).isEqualTo(Shelter.OUTDOOR);
        assertThat(of("shinto_shrine,tourist_attraction")).isEqualTo(Shelter.OUTDOOR);
        assertThat(of("buddhist_temple,museum")).isEqualTo(Shelter.OUTDOOR);
        assertThat(of("zoo")).isEqualTo(Shelter.OUTDOOR);
    }

    @Test
    void 거리_시장처럼_애매한_곳은_모름으로_두고_바꾸지_않는다() {
        assertThat(of("tourist_attraction")).isEqualTo(Shelter.UNKNOWN);
        assertThat(of("market,tourist_attraction")).isEqualTo(Shelter.UNKNOWN);
        assertThat(Shelter.of(Attraction.builder().build())).isEqualTo(Shelter.UNKNOWN);
    }
}
