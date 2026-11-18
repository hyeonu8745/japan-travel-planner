package com.example.japantravelplanner.itinerary.candidate;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.attraction.entity.AttractionKind;
import com.example.japantravelplanner.itinerary.planner.DayWindow;
import com.example.japantravelplanner.itinerary.planner.StayTimes;
import com.example.japantravelplanner.itinerary.zone.TripZone;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 하루를 쓰는 테마파크 (11-18: USJ 를 두 시간 보고 다른 곳을 도는 일정이 나왔다) */
class FullDayParksTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);
    private static final DayWindow FULL = new DayWindow(LocalTime.of(9, 30), LocalTime.of(21, 0), true, true);
    private static final DayWindow LATE_ARRIVAL = new DayWindow(LocalTime.of(14, 0), LocalTime.of(21, 0), false, true);

    private static ScoredAttraction spot(long id, String name, AttractionKind kind) {
        Attraction a = Attraction.builder().id(id).name(name).kind(kind)
                .location(GF.createPoint(new Coordinate(135.43, 34.66))).build();
        return new ScoredAttraction(a, TripZone.OSAKA_BAY, 5.0 - id * 0.1);
    }

    private static final ScoredAttraction USJ = spot(1, "ユニバーサル・スタジオ・ジャパン", AttractionKind.THEME);
    private static final ScoredAttraction AQUARIUM = spot(2, "海遊館", AttractionKind.THEME);
    private static final ScoredAttraction TSUTENKAKU = spot(3, "通天閣", AttractionKind.VIEW);

    private static DayZone day(List<ScoredAttraction> candidates) {
        return new DayZone(2, LocalDate.of(2026, 12, 8), TripZone.OSAKA_BAY, candidates);
    }

    @Test
    void 하루를_다_쓰는_날에_파크가_방문_후보면_그날은_파크_하나만() {
        DayZone shaped = FullDayParks.shape(day(List.of(USJ, AQUARIUM, TSUTENKAKU)), 3, FULL, 4);

        assertThat(shaped.candidates()).containsExactly(USJ);
        assertThat(FullDayParks.isParkDay(shaped)).isTrue();
    }

    @Test
    void 늦게_도착한_날이나_예비_후보일_때는_파크를_뺀다() {
        assertThat(FullDayParks.shape(day(List.of(USJ, AQUARIUM, TSUTENKAKU)), 3, LATE_ARRIVAL, 4).candidates())
                .containsExactly(AQUARIUM, TSUTENKAKU);
        // 방문 수(2) 밖의 예비 후보로만 있으면 짧게 들어가지 않게 뺀다
        assertThat(FullDayParks.shape(day(List.of(AQUARIUM, TSUTENKAKU, USJ)), 2, FULL, 4).candidates())
                .containsExactly(AQUARIUM, TSUTENKAKU);
    }

    @Test
    void 이틀_이하_여행에는_파크_날을_만들지_않는다() {
        // 11-18: 2일 가와구치코 여행의 하루가 후지큐 하이랜드로 채워졌다
        assertThat(FullDayParks.shape(day(List.of(USJ, AQUARIUM, TSUTENKAKU)), 3, FULL, 2).candidates())
                .containsExactly(AQUARIUM, TSUTENKAKU);
    }

    @Test
    void 파크가_없는_날은_그대로() {
        DayZone plain = day(List.of(AQUARIUM, TSUTENKAKU));

        assertThat(FullDayParks.shape(plain, 2, FULL, 4)).isSameAs(plain);
        assertThat(FullDayParks.isParkDay(plain)).isFalse();
    }

    @Test
    void 파크_체류는_7시간이고_여행_속도_배율을_곱하지_않는다() {
        assertThat(StayTimes.of(USJ.attraction(), 1.3)).isEqualTo(FullDayParks.STAY_MINUTES);
        assertThat(StayTimes.of(AQUARIUM.attraction(), 1.0)).isEqualTo(120);   // 수족관은 놀이공원 체류
    }
}
