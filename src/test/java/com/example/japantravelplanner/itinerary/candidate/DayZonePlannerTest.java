package com.example.japantravelplanner.itinerary.candidate;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.itinerary.zone.TripZone;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 날짜별 관광지 후보: 기본은 지역 안에서 종류·위치를 섞고, 가까운 곳 위주면 점수순 */
class DayZonePlannerTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);
    private final DayZonePlanner planner = new DayZonePlanner(new CandidateProperties());

    private static ScoredAttraction spot(long id, String name, String types, double lat, double lng, double score) {
        Attraction a = Attraction.builder().id(id).name(name).types(types)
                .location(GF.createPoint(new Coordinate(lng, lat))).build();
        return new ScoredAttraction(a, TripZone.GINZA_TOKYO, score);
    }

    // 10-10 여행 8 v9 2일차와 비슷한 긴자·시오도메 쪽 (실제 좌표)
    private final ScoredAttraction hamarikyu = spot(1, "浜離宮恩賜庭園", "garden,park", 35.6596559, 139.7634091, 4.80);
    private final ScoredAttraction pine = spot(2, "三百年の松", "scenic_spot,historical_landmark", 35.6624093, 139.7639592, 4.75);
    private final ScoredAttraction shibarikyu = spot(3, "旧芝離宮恩賜庭園", "garden", 35.6549505, 139.7587206, 4.70);
    private final ScoredAttraction tsukishima = spot(4, "月島もんじゃストリート", "market", 35.6642944, 139.7824662, 4.60);
    private final ScoredAttraction ginza = spot(5, "銀座歩行者天国", "tourist_attraction", 35.6717, 139.7650, 4.58);
    private final ScoredAttraction marunouchi = spot(6, "丸の内仲通り", "tourist_attraction", 35.6797, 139.7637, 4.50);
    private final List<ScoredAttraction> pool = List.of(hamarikyu, pine, shibarikyu, tsukishima, ginza, marunouchi);

    @Test
    void 기본은_같은_종류와_가까운_곳을_뒤로_미뤄_지역_안에서_섞는다() {
        List<ScoredAttraction> picked = planner.diverse(pool, 4);

        // 浜離宮 다음 점수인 三百年の松(정원 안, 387m)은 맨 뒤로, 두 번째 정원 旧芝離宮은 다른 종류 뒤로 밀린다
        assertThat(picked).containsExactly(hamarikyu, tsukishima, ginza, shibarikyu);
        assertThat(picked).doesNotContain(pine);
    }

    @Test
    void 가까운_곳_위주면_예전처럼_점수순() {
        List<DayZone> days = planner.plan(pool, LocalDate.of(2026, 10, 13), 1, 4, List.of(), List.of(),
                35.6812, 139.7671, true);

        assertThat(days.get(0).candidates()).startsWith(hamarikyu, pine, shibarikyu, tsukishima);
    }

    @Test
    void 섞어서_고른_순서가_날짜의_후보_순서가_된다() {
        List<DayZone> days = planner.plan(pool, LocalDate.of(2026, 10, 13), 1, 4, List.of(), List.of(),
                35.6812, 139.7671, false);

        assertThat(days.get(0).candidates()).startsWith(hamarikyu, tsukishima, ginza, shibarikyu);
        assertThat(days.get(0).candidates()).hasSize(6);   // 하루 4곳 + 예비 2곳
    }

    @Test
    void 같은_가게의_다른_지점은_여행_전체에서_한_번만() {
        // 10-10 여행 8 v10: Chopsticks Studio Ginza / Shinjuku / Shibuya가 날마다 하나씩 들어갔다
        ScoredAttraction ginzaStudio = new ScoredAttraction(Attraction.builder().id(31L).name("Chopsticks Studio Ginza")
                .types("tourist_attraction").location(GF.createPoint(new Coordinate(139.7694, 35.6731))).build(),
                TripZone.GINZA_TOKYO, 4.9);
        ScoredAttraction shinjukuStudio = new ScoredAttraction(Attraction.builder().id(32L).name("Chopsticks Studio Shinjuku")
                .types("tourist_attraction").location(GF.createPoint(new Coordinate(139.7030, 35.6935))).build(),
                TripZone.SHINJUKU, 4.9);
        ScoredAttraction gyoen = new ScoredAttraction(Attraction.builder().id(33L).name("新宿御苑")
                .types("park").location(GF.createPoint(new Coordinate(139.7100, 35.6852))).build(),
                TripZone.SHINJUKU, 4.8);

        List<DayZone> days = planner.plan(List.of(ginzaStudio, shinjukuStudio, gyoen), LocalDate.of(2026, 10, 13),
                2, 1, List.of(), List.of(), 35.6812, 139.7671, false);

        List<String> names = days.stream().flatMap(d -> d.candidates().stream())
                .map(c -> c.attraction().getName()).toList();
        assertThat(names).containsOnlyOnce("Chopsticks Studio Ginza").doesNotContain("Chopsticks Studio Shinjuku");
        assertThat(names).contains("新宿御苑");
    }

    @Test
    void 지점_이름을_뺀_가게_이름() {
        assertThat(BrandKey.of("Chopsticks Studio Ginza")).isEqualTo("Chopsticks Studio");
        assertThat(BrandKey.of("Fennec House 新大久保店")).isEqualTo("Fennec House");
        assertThat(BrandKey.of("SHIBUYA SKY")).isEqualTo("SHIBUYA SKY");
        assertThat(BrandKey.of("浅草寺")).isEqualTo("浅草寺");
        assertThat(BrandKey.of("東京都庁第一本庁舎 北展望室")).isEqualTo("東京都庁第一本庁舎 北展望室");
    }

    @Test
    void 조정_점수는_같은_종류_수와_가까움만큼_뺀다() {
        CandidateProperties props = new CandidateProperties();
        // 三百年の松: 浜離宮과 387m(가까움). 종류는 NATURE(scenic_spot)라 정원과 같은 종류
        double value = planner.adjustedScore(pine, List.of(hamarikyu));
        assertThat(value).isEqualTo(4.75 - props.getSameKindPenalty() - props.getNearPenalty(), org.assertj.core.data.Offset.offset(1e-9));
        assertThat(planner.adjustedScore(marunouchi, List.of(tsukishima))).isEqualTo(4.50, org.assertj.core.data.Offset.offset(1e-9));
        // 종류를 모르는 곳(OTHER)끼리는 종류 감점이 없다 (긴자와 마루노우치는 약 900m라 가까움 감점도 없음)
        assertThat(planner.adjustedScore(marunouchi, List.of(ginza))).isEqualTo(4.50, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void 띄어쓰기_없이_지역이_붙은_지점도_같은_가게로_본다() {
        // 10-11 여행 8 v12: 忍者体験カフェ原宿가 3·5·6일차, 浅草 지점까지 후보에 있었다
        assertThat(BrandKey.of("忍者体験カフェ原宿")).isEqualTo(BrandKey.of("忍者体験カフェ浅草"));
        assertThat(BrandKey.of("新宿御苑")).isEqualTo("新宿御苑");
        assertThat(BrandKey.of("西新宿")).isEqualTo("西新宿");   // 앞부분이 짧으면 지역 이름 자체
        assertThat(BrandKey.of("浅草寺")).isEqualTo("浅草寺");
    }
}
