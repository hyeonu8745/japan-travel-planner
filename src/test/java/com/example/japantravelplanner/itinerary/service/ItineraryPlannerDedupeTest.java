package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.itinerary.candidate.BrandKey;
import com.example.japantravelplanner.itinerary.candidate.DayZone;
import com.example.japantravelplanner.itinerary.candidate.ScoredAttraction;
import com.example.japantravelplanner.itinerary.zone.TripZone;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 앞 날짜에 들어간 관광지는 뒤 날짜 후보에서 뺀다 (10-11).
 * 하루 두 지역이 되면서 예비 후보가 다른 날 후보와 겹쳐, 여행 8 v12에서 忍者体験カフェ原宿가 3·5·6일차,
 * 小網神社가 2·4일차에 들어갔다.
 */
class ItineraryPlannerDedupeTest {

    private static ScoredAttraction spot(long id, String name) {
        return new ScoredAttraction(Attraction.builder().id(id).name(name).types("tourist_attraction").build(),
                TripZone.SHIBUYA_HARAJUKU, 4.5);
    }

    @Test
    void 앞_날짜에_들어간_관광지와_같은_가게의_다른_지점을_뺀다() {
        ScoredAttraction ninjaHarajuku = spot(1, "忍者体験カフェ原宿");
        ScoredAttraction ninjaAsakusa = spot(2, "忍者体験カフェ浅草");
        ScoredAttraction koami = spot(3, "小網神社");
        ScoredAttraction meiji = spot(4, "明治神宮");
        DayZone day = new DayZone(5, LocalDate.of(2026, 10, 16), TripZone.SHINJUKU,
                List.of(ninjaAsakusa, koami, meiji), TripZone.SHIBUYA_HARAJUKU);

        DayZone filtered = ItineraryPlanner.withoutUsed(day, Set.of(1L, 3L),
                Set.of(BrandKey.of(ninjaHarajuku.attraction().getName()), BrandKey.of("小網神社")));

        assertThat(filtered.candidates()).containsExactly(meiji);
        assertThat(filtered.partner()).isEqualTo(TripZone.SHIBUYA_HARAJUKU);
    }

    @Test
    void 첫날은_그대로() {
        DayZone day = new DayZone(1, LocalDate.of(2026, 10, 12), TripZone.SHINJUKU, List.of(spot(1, "明治神宮")));

        assertThat(ItineraryPlanner.withoutUsed(day, Set.of(), Set.of())).isSameAs(day);
    }
}
