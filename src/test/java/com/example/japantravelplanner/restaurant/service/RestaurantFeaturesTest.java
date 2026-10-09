package com.example.japantravelplanner.restaurant.service;

import com.example.japantravelplanner.global.google.GooglePlacesClient.Money;
import com.example.japantravelplanner.global.google.GooglePlacesClient.RestaurantPlace;
import com.example.japantravelplanner.restaurant.dto.RestaurantDetailResponseDto.FeaturesDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RestaurantFeaturesTest {

    @Test
    void 가격은_엔화일_때만_정수로() {
        assertThat(RestaurantFeatures.yen(new Money("JPY", "3000", 0))).isEqualTo(3000);
        assertThat(RestaurantFeatures.yen(new Money("USD", "30", 0))).isNull();
        assertThat(RestaurantFeatures.yen(new Money("JPY", "abc", 0))).isNull();
        assertThat(RestaurantFeatures.yen(null)).isNull();
    }

    @Test
    void 특성_정보가_하나도_없으면_모두_null이고_이름도_없다() {
        RestaurantPlace empty = new RestaurantPlace("p", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);

        FeaturesDto features = RestaurantFeatures.from(empty);

        assertThat(features.labels()).isEmpty();
        assertThat(features.servesLunch()).isNull();
        assertThat(features.acceptsCreditCards()).isNull();
        assertThat(features.priceMinYen()).isNull();
    }

    @Test
    void 현금만_받는_가게는_현금만으로_표시한다() {
        RestaurantPlace cashOnly = new RestaurantPlace("p", null, null, null, null, null, null, null, null, null,
                null, null, true, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                new com.example.japantravelplanner.global.google.GooglePlacesClient.PaymentOptions(false, false, true, false));

        assertThat(RestaurantFeatures.from(cashOnly).labels()).containsExactly("점심", "현금만");
    }
}
