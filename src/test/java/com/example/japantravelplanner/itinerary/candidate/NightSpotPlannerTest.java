package com.example.japantravelplanner.itinerary.candidate;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.itinerary.planner.DayWindow;
import com.example.japantravelplanner.itinerary.zone.TripZone;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 여행 전체를 보고 야경 장소 나누기 (대표 명소만, 종류 섞기) */
class NightSpotPlannerTest {

    private static final DayWindow FULL_DAY = new DayWindow(LocalTime.of(9, 30), LocalTime.of(22, 0), true, true);
    private static final DayWindow LATE_ARRIVAL = new DayWindow(LocalTime.of(19, 0), LocalTime.of(22, 0), false, true);
    private static final DayWindow NO_DINNER = new DayWindow(LocalTime.of(9, 30), LocalTime.of(12, 0), true, false);

    private static long nextId = 1;

    private static ScoredAttraction spot(String name, String types, TripZone zone, double score) {
        Attraction a = Attraction.builder().id(nextId++).name(name).types(types).build();
        return new ScoredAttraction(a, zone, score);
    }

    // 10-10 여행 8의 관광지 데이터 (점수는 대략)
    private final ScoredAttraction tower = spot("東京タワー", "observation_deck,landmark", TripZone.ROPPONGI_TOWER, 4.8);
    private final ScoredAttraction hills = spot("六本木ヒルズ展望台 東京シティビュー", "observation_deck", TripZone.ROPPONGI_TOWER, 4.7);
    private final ScoredAttraction skyView = spot("SKY VIEW", "observation_deck", TripZone.GINZA_TOKYO, 4.6);
    private final ScoredAttraction seaside = spot("お台場海浜公園", "park", TripZone.ODAIBA, 4.4);
    private final ScoredAttraction rainbow = spot("レインボーブリッジ", "bridge", TripZone.ODAIBA, 4.8);
    private final ScoredAttraction tocho = spot("東京都庁第一本庁舎 北展望室", "observation_deck", TripZone.SHINJUKU, 4.8);
    private final ScoredAttraction kabukicho = spot("歌舞伎町一番街アーチ", "cultural_landmark", TripZone.SHINJUKU, 4.3);
    private final ScoredAttraction shibuyaSky = spot("SHIBUYA SKY", "observation_deck", TripZone.SHIBUYA_HARAJUKU, 4.9);
    private final ScoredAttraction scramble = spot("渋谷スクランブル交差点", "", TripZone.SHIBUYA_HARAJUKU, 4.5);
    private final ScoredAttraction sensoji = spot("浅草寺", "buddhist_temple", TripZone.ASAKUSA_UENO, 4.9);
    private final List<ScoredAttraction> all = List.of(shibuyaSky, sensoji, tower, rainbow, tocho, hills, skyView,
            scramble, seaside, kabukicho);

    private static DayZone day(int number, TripZone zone) {
        return new DayZone(number, LocalDate.of(2026, 10, 11 + number), zone, List.of());
    }

    @Test
    void 여행_8은_전망대_네_번_대신_종류를_섞는다() {
        // 1일차 19시 시작(늦은 도착), 7일차 저녁 없음 → 야경 없음
        List<DayZone> days = List.of(day(1, TripZone.ASAKUSA_UENO), day(2, TripZone.GINZA_TOKYO),
                day(3, TripZone.ROPPONGI_TOWER), day(4, TripZone.ODAIBA), day(5, TripZone.SHINJUKU),
                day(6, TripZone.SHIBUYA_HARAJUKU), day(7, TripZone.AKIHABARA_KANDA));
        List<DayWindow> windows = List.of(LATE_ARRIVAL, FULL_DAY, FULL_DAY, FULL_DAY, FULL_DAY, FULL_DAY, NO_DINNER);

        Map<Integer, ScoredAttraction> nights = NightSpotPlanner.assign(days, all, windows);

        assertThat(nights).containsOnlyKeys(3, 4, 5, 6);
        assertThat(nights.get(6)).isEqualTo(shibuyaSky);     // 점수 가장 높은 곳이 먼저
        assertThat(nights.get(4)).isEqualTo(seaside);        // 다리 자체(산책로는 밤에 닫음)가 아니라 해변
        assertThat(nights.get(5)).isEqualTo(kabukicho);      // 전망대(都庁)보다 아직 안 나온 거리 야경
        assertThat(nights.get(3)).isEqualTo(tower);          // 전망대 두 번째
        assertThat(nights.values()).doesNotContain(skyView); // 대표 명소가 아닌 전망대는 넣지 않는다 (2일차는 야경 없음)
    }

    @Test
    void 같은_종류는_여행_전체에서_두_번까지() {
        List<DayZone> days = List.of(day(1, TripZone.ROPPONGI_TOWER), day(2, TripZone.SHINJUKU),
                day(3, TripZone.SHIBUYA_HARAJUKU));
        List<ScoredAttraction> decksOnly = List.of(tower, tocho, shibuyaSky);

        Map<Integer, ScoredAttraction> nights = NightSpotPlanner.assign(days,
                decksOnly, List.of(FULL_DAY, FULL_DAY, FULL_DAY));

        assertThat(nights).hasSize(2);
        assertThat(nights.values()).containsExactlyInAnyOrder(shibuyaSky, tower);   // 점수 높은 두 곳
    }

    @Test
    void 같은_지역이_두_번_나와도_같은_장소를_두_번_넣지_않는다() {
        List<DayZone> days = List.of(day(1, TripZone.ROPPONGI_TOWER), day(2, TripZone.ROPPONGI_TOWER));

        Map<Integer, ScoredAttraction> nights = NightSpotPlanner.assign(days, List.of(tower, hills),
                List.of(FULL_DAY, FULL_DAY));

        assertThat(nights.values()).containsExactlyInAnyOrder(tower, hills);
    }

    @Test
    void 저녁이_없거나_저녁_뒤에_시작하는_날은_야경이_없다() {
        assertThat(NightSpotPlanner.eligible(FULL_DAY)).isTrue();
        assertThat(NightSpotPlanner.eligible(new DayWindow(LocalTime.of(18, 0), LocalTime.of(22, 0), false, true))).isTrue();
        assertThat(NightSpotPlanner.eligible(LATE_ARRIVAL)).isFalse();
        assertThat(NightSpotPlanner.eligible(NO_DINNER)).isFalse();
        assertThat(NightSpotPlanner.eligible(new DayWindow(null, null, false, false))).isFalse();
    }

    @Test
    void 야경_종류와_자리_교체_후보() {
        assertThat(NightSpots.kindOf(tower.attraction())).isEqualTo(NightSpots.Kind.OBSERVATORY);
        assertThat(NightSpots.kindOf(seaside.attraction())).isEqualTo(NightSpots.Kind.WATERFRONT);
        assertThat(NightSpots.kindOf(sensoji.attraction())).isEqualTo(NightSpots.Kind.LIGHT_UP);
        assertThat(NightSpots.kindOf(rainbow.attraction())).isNull();          // 다리 산책로는 밤에 닫는다
        assertThat(NightSpots.isRepresentative(skyView.attraction())).isFalse();
        assertThat(NightSpots.isNightCandidate(skyView.attraction())).isTrue(); // 직접 바꿀 때는 전망대도 후보
        assertThat(NightSpots.isNightCandidate(spot("公園", "park", TripZone.SHINJUKU, 4.0).attraction())).isFalse();
    }
}
