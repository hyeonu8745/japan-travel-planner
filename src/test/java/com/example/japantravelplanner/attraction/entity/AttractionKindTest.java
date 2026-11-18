package com.example.japantravelplanner.attraction.entity;

import com.example.japantravelplanner.itinerary.candidate.PlaceKind;
import com.example.japantravelplanner.itinerary.candidate.Shelter;
import com.example.japantravelplanner.itinerary.planner.StayTimes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 확장 데이터 종류(kind) → Google type 이름표: 체류 시간·종류 섞기·실내외 판단이 Google 데이터와 같게 동작한다 */
class AttractionKindTest {

    private static Attraction of(String name, AttractionKind kind) {
        return Attraction.builder().name(name).kind(kind).build();
    }

    @Test
    void Google_types가_있으면_그대로_쓴다() {
        Attraction old = Attraction.builder().name("浅草寺").kind(AttractionKind.TEMPLE)
                .types("buddhist_temple,tourist_attraction,point_of_interest").build();

        assertThat(old.typesOrKind()).isEqualTo("buddhist_temple,tourist_attraction,point_of_interest");
    }

    @Test
    void types가_없으면_kind를_같은_이름표로_바꾼다() {
        assertThat(of("増上寺", AttractionKind.TEMPLE).typesOrKind()).contains("buddhist_temple");
        assertThat(of("東京国立博物館", AttractionKind.MUSEUM).typesOrKind()).contains("museum");
        assertThat(Attraction.builder().name("이름만").build().typesOrKind()).isNull();
    }

    @Test
    void 이름에_전망대_말이_있는_VIEW와_ATTRACTION은_전망대다() {
        assertThat(of("Shibuya Sky", AttractionKind.VIEW).typesOrKind()).contains("observation_deck");
        assertThat(of("東京シティビュー", AttractionKind.VIEW).typesOrKind()).contains("observation_deck");
        assertThat(of("東京タワー", AttractionKind.ATTRACTION).typesOrKind()).contains("observation_deck");
        assertThat(of("東京スカイツリー", AttractionKind.ATTRACTION).typesOrKind()).contains("observation_deck");
    }

    @Test
    void 산이나_공원의_展望台는_밖에서_보는_경치라_전망대_건물이_아니다() {
        Attraction lookout = of("天狗山展望台", AttractionKind.VIEW);

        assertThat(lookout.typesOrKind()).doesNotContain("observation_deck");
        assertThat(Shelter.of(lookout)).isEqualTo(Shelter.OUTDOOR);
    }

    @Test
    void 체류_시간과_종류가_kind로도_정해진다() {
        assertThat(StayTimes.baseOf(of("東京タワー", AttractionKind.ATTRACTION).typesOrKind())).isEqualTo(60);
        assertThat(StayTimes.baseOf(of("東京国立博物館", AttractionKind.MUSEUM).typesOrKind())).isEqualTo(90);
        assertThat(StayTimes.baseOf(of("皇居", AttractionKind.CASTLE).typesOrKind())).isEqualTo(75);
        assertThat(PlaceKind.of(of("明治神宮外苑", AttractionKind.PARK))).isEqualTo(PlaceKind.NATURE);
        assertThat(Shelter.of(of("国立科学博物館", AttractionKind.MUSEUM))).isEqualTo(Shelter.INDOOR);
    }
}
