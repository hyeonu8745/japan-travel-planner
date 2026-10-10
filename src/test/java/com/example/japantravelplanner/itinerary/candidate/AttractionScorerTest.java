package com.example.japantravelplanner.itinerary.candidate;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.itinerary.zone.ZoneAssigner;
import com.example.japantravelplanner.trip.entity.Interest;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AttractionScorerTest {

    private static final double MEAN = 4.3;
    private static final int PRIOR = 200;

    @Test
    void 리뷰가_적으면_전체_평균에_가까워진다() {
        double godzilla = AttractionScorer.bayesian(5.0, 2, MEAN, PRIOR);       // 리뷰 2개, 5.0
        assertThat(godzilla).isBetween(4.30, 4.32);
    }

    @Test
    void 리뷰가_많으면_자기_평점에_가깝다() {
        double sensoji = AttractionScorer.bayesian(4.6, 99_070, MEAN, PRIOR);   // 리뷰 10만, 4.6
        assertThat(sensoji).isBetween(4.59, 4.61);
    }

    @Test
    void 리뷰가_많은_곳이_리뷰가_적은_고평점보다_위에_온다() {
        double gyoenSakura = AttractionScorer.bayesian(4.7, 395, MEAN, PRIOR);  // 신주쿠교엔 벚꽃 정원
        double sensoji = AttractionScorer.bayesian(4.6, 99_070, MEAN, PRIOR);
        double godzilla = AttractionScorer.bayesian(5.0, 2, MEAN, PRIOR);

        assertThat(sensoji).isGreaterThan(gyoenSakura);   // 12-1편에서 발견한 문제 해결
        assertThat(gyoenSakura).isGreaterThan(godzilla);
    }

    @Test
    void 같은_건물이나_공원의_일부는_중복으로_본다() {
        assertThat(AttractionScorer.isNearDuplicate(
                "東京都庁", 35.6894807, 139.6916863,
                "東京都庁第一本庁舎 北展望室", 35.689733, 139.6916563)).isTrue();
        assertThat(AttractionScorer.isNearDuplicate(
                "新宿御苑", 35.6851763, 139.7100517,
                "新宿御苑 桜園地", 35.6822873, 139.7101213)).isTrue();
    }

    @Test
    void 가까워도_이름이_다르면_다른_관광지다() {
        // 센소지와 아사쿠사 신사는 바로 옆이지만 다른 곳
        assertThat(AttractionScorer.isNearDuplicate(
                "浅草寺", 35.7147651, 139.7966553,
                "浅草神社", 35.7151665, 139.7974389)).isFalse();
    }

    @Test
    void 큰_공원은_수백_m_떨어져도_같은_장소다() {
        // 신주쿠교엔 대표 좌표와 벚꽃 정원은 약 320m
        assertThat(AttractionScorer.isNearDuplicate(
                "新宿御苑", 35.6851763, 139.7100517,
                "新宿御苑 桜園地", 35.6822873, 139.7101213)).isTrue();
    }

    // ── 이름이 달라도 아주 가까우면 같은 장소 ──

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);
    private final AttractionScorer scorer = new AttractionScorer(new CandidateProperties(), new ZoneAssigner());

    private static Attraction place(long id, String name, double lat, double lng, double rating) {
        return Attraction.builder().id(id).name(name).rating(rating).reviewCnt(50_000).types("tourist_attraction")
                .location(GF.createPoint(new Coordinate(lng, lat))).build();
    }

    private List<String> keptNames(List<Attraction> attractions) {
        return scorer.score(attractions, List.of()).stream().map(s -> s.attraction().getName()).toList();
    }

    @Test
    void 센소지_경내의_신사는_센소지에_합쳐진다() {
        // 실제 좌표: 센소지와 아사쿠사 신사는 84m
        List<String> kept = keptNames(List.of(
                place(1, "浅草寺", 35.7147651, 139.7966553, 4.6),
                place(2, "浅草神社", 35.7151665, 139.7974389, 4.5)));
        assertThat(kept).containsExactly("浅草寺");
    }

    @Test
    void 이름이_달라도_43m_거리면_점수_높은_쪽만_남는다() {
        // 愛宕山과 出世の石段 (실제 거리 43m, 이름은 서로 포함하지 않음)
        List<String> kept = keptNames(List.of(
                place(1, "愛宕山", 35.6655, 139.7450, 4.45),
                place(2, "出世の石段", 35.6659, 139.7450, 4.49)));
        assertThat(kept).containsExactly("出世の石段");
    }

    @Test
    void 기준_거리_밖이면_둘_다_남는다() {
        // 센소지에서 북쪽으로 약 178m (花やしき 정도의 거리)
        List<String> kept = keptNames(List.of(
                place(1, "浅草寺", 35.7147651, 139.7966553, 4.6),
                place(2, "浅草花やしき", 35.7163651, 139.7966553, 4.3)));
        assertThat(kept).containsExactlyInAnyOrder("浅草寺", "浅草花やしき");
    }

    @Test
    void 줄줄이_이어진_곳은_통째로_합쳐지지_않는다() {
        // A-B 약 140m, B-C 약 140m, A-C 약 280m. 점수는 A > B > C
        // B는 A와 가까워 빠지지만, C는 남아 있는 A와는 멀어서 남는다
        List<String> kept = keptNames(List.of(
                place(1, "A", 35.6890, 139.7040, 4.9),
                place(2, "B", 35.6902, 139.7040, 4.8),
                place(3, "C", 35.6915, 139.7040, 4.7)));
        assertThat(kept).containsExactly("A", "C");
    }

    @Test
    void 야경_관심사면_대표_야경_명소는_가까운_곳에_합쳐지지_않는다() {
        // 10-10 여행 8 v9: 歌舞伎町一番街アーチ가 137m 옆 新宿東口の猫(점수 더 높음)에 합쳐져 신주쿠 야경이 비었다
        List<Attraction> places = List.of(
                place(1, "新宿東口の猫", 35.6926516, 139.7008030, 4.5),
                place(2, "歌舞伎町一番街アーチ", 35.6938600, 139.7011000, 4.4));

        assertThat(keptNames(places)).containsExactly("新宿東口の猫");   // 야경 관심사가 없으면 예전처럼
        assertThat(scorer.score(places, List.of(Interest.NIGHT_VIEW)).stream().map(s -> s.attraction().getName()))
                .containsExactlyInAnyOrder("新宿東口の猫", "歌舞伎町一番街アーチ");
    }

    @Test
    void 대표_야경_명소와_이름이_겹치는_같은_장소는_합친다() {
        // 10-10 v11: 낮에 お台場海浜公園の展望デッキ, 밤에 お台場海浜公園 (같은 공원)
        List<Attraction> places = List.of(
                place(1, "お台場海浜公園", 35.6300488, 139.7756912, 4.4),
                place(2, "お台場海浜公園の展望デッキ", 35.6275906, 139.7714420, 4.7));

        assertThat(scorer.score(places, List.of(Interest.NIGHT_VIEW)).stream().map(s -> s.attraction().getName()))
                .containsExactly("お台場海浜公園");
    }

    @Test
    void 가까운_거리_판정은_경계를_포함한다() {
        assertThat(AttractionScorer.isSameSpot(35.0, 139.0, 35.0, 139.0, 150)).isTrue();
        assertThat(AttractionScorer.isSameSpot(35.0, 139.0, 35.002, 139.0, 150)).isFalse();   // 약 222m
    }

    // ── 리뷰가 너무 적은 곳은 후보에서 뺀다 ──

    private static Attraction placeWithReviews(long id, String name, int reviews, double rating) {
        return Attraction.builder().id(id).name(name).rating(rating).reviewCnt(reviews).types("tourist_attraction")
                .location(GF.createPoint(new Coordinate(139.7040 + id * 0.01, 35.6890))).build();
    }

    @Test
    void 리뷰가_100개_미만인_곳은_후보에서_빠진다() {
        // 실제 사례: なだれ坂(리뷰 36개)가 하루 일정에 들어가 이동만 30분 늘렸다
        List<String> kept = keptNames(List.of(
                placeWithReviews(1, "新宿御苑", 45_733, 4.6),
                placeWithReviews(2, "なだれ坂", 36, 4.3),
                placeWithReviews(3, "ゴジラロード", 2, 5.0)));

        assertThat(kept).containsExactly("新宿御苑");
    }

    @Test
    void 리뷰_100개는_후보에_남는다() {
        List<String> kept = keptNames(List.of(
                placeWithReviews(1, "백개", 100, 4.4),
                placeWithReviews(2, "아흔아홉개", 99, 4.4)));

        assertThat(kept).containsExactly("백개");
    }

    @Test
    void 리뷰_수를_모르는_곳도_빠진다() {
        Attraction unknown = Attraction.builder().id(1L).name("리뷰없음").rating(4.5).types("tourist_attraction")
                .location(GF.createPoint(new Coordinate(139.7040, 35.6890))).build();

        assertThat(keptNames(List.of(unknown))).isEmpty();
    }

}