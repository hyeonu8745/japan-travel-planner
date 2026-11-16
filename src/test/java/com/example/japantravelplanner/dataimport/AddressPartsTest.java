package com.example.japantravelplanner.dataimport;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AddressPartsTest {

    @Test
    void tokyoWard() {
        assertThat(AddressParts.of("東京都千代田区飯田橋4-7-11 カクタス飯田橋 B1F"))
                .isEqualTo(new AddressParts("東京", "千代田区"));
    }

    @Test
    void wardThatContainsCityCharacterIsNotCut() {
        // 市ヶ谷 가 주소 뒤쪽에 있어도 구 이름까지만
        assertThat(AddressParts.of("東京都新宿区市谷田町1-1").municipality()).isEqualTo("新宿区");
    }

    @Test
    void designatedCityKeepsWard() {
        assertThat(AddressParts.of("大阪府大阪市中央区難波1-1")).isEqualTo(new AddressParts("大阪", "大阪市中央区"));
        assertThat(AddressParts.of("京都府京都市東山区清水1-294")).isEqualTo(new AddressParts("京都", "京都市東山区"));
        assertThat(AddressParts.of("神奈川県横浜市中区山下町1")).isEqualTo(new AddressParts("神奈川", "横浜市中区"));
    }

    @Test
    void plainCityTownAndHokkaido() {
        assertThat(AddressParts.of("神奈川県鎌倉市雪ノ下2-1-31")).isEqualTo(new AddressParts("神奈川", "鎌倉市"));
        assertThat(AddressParts.of("北海道小樽市色内1")).isEqualTo(new AddressParts("北海道", "小樽市"));
        assertThat(AddressParts.of("神奈川県足柄下郡箱根町湯本1")).isEqualTo(new AddressParts("神奈川", "足柄下郡箱根町"));
        assertThat(AddressParts.of("大分県由布市湯布院町川上1")).isEqualTo(new AddressParts("大分", "由布市"));
    }

    @Test
    void unknownShapeGivesNulls() {
        assertThat(AddressParts.of(null)).isEqualTo(new AddressParts(null, null));
        assertThat(AddressParts.of("  ")).isEqualTo(new AddressParts(null, null));
        assertThat(AddressParts.of("Tokyo Chiyoda")).isEqualTo(new AddressParts(null, null));
    }
}
