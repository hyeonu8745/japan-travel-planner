package com.example.japantravelplanner.itinerary.candidate;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.trip.entity.Interest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InterestTypesTest {

    private static Attraction attraction(String name, String types) {
        return Attraction.builder().name(name).types(types).build();
    }

    @Test
    void 주방용품_거리와_할인점은_현지_음식이_아니다() {
        // 10-10 요약: "관심사 '현지 음식'에 맞는 곳: アメ横商店街, かっぱ橋道具街"
        assertThat(InterestTypes.matches(Interest.FOOD,
                attraction("かっぱ橋道具街", "tourist_attraction,market,point_of_interest"))).isFalse();
        assertThat(InterestTypes.matches(Interest.FOOD,
                attraction("合羽橋本通り商店街", "market,point_of_interest"))).isFalse();
        assertThat(InterestTypes.matches(Interest.FOOD,
                attraction("ドン・キホーテ 浅草店", "discount_store,general_store,market,store"))).isFalse();
    }

    @Test
    void 먹거리_시장은_현지_음식이다() {
        assertThat(InterestTypes.matches(Interest.FOOD,
                attraction("アメ横商店街", "tourist_attraction,market,point_of_interest"))).isTrue();
        assertThat(InterestTypes.matches(Interest.FOOD,
                attraction("豊洲市場", "market,wholesaler,food"))).isTrue();
    }

    @Test
    void 쇼핑_관심사로는_갓파바시도_그대로_쇼핑이다() {
        assertThat(InterestTypes.matches(Interest.SHOPPING,
                attraction("かっぱ橋道具街", "tourist_attraction,market,point_of_interest"))).isTrue();
    }
}
