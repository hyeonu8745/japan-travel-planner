package com.example.japantravelplanner.restaurant.match;

import com.example.japantravelplanner.global.google.GooglePlacesClient.LatLng;
import com.example.japantravelplanner.global.google.GooglePlacesClient.LocalizedText;
import com.example.japantravelplanner.global.google.GooglePlacesClient.SearchPlace;
import com.example.japantravelplanner.restaurant.entity.GoogleMatchStatus;
import com.example.japantravelplanner.restaurant.match.RestaurantPlaceMatcher.Decision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 같은 가게 고르기. 사례는 10-09 표본 50곳 측정에서 나온 실제 이름들 */
class RestaurantPlaceMatcherTest {

    private static final double LAT = 35.70;
    private static final double LNG = 139.70;
    private static final double METERS_PER_DEGREE_LAT = 111_000;

    /** 우리 좌표에서 북쪽으로 meters 떨어진 검색 결과 */
    private static SearchPlace place(String id, String name, double meters, String businessStatus) {
        return new SearchPlace(id, new LocalizedText(name, "ja"),
                new LatLng(LAT + meters / METERS_PER_DEGREE_LAT, LNG), businessStatus);
    }

    private static SearchPlace place(String id, String name, double meters) {
        return place(id, name, meters, "OPERATIONAL");
    }

    private static Decision decide(String name, SearchPlace... places) {
        return RestaurantPlaceMatcher.decide(name, LAT, LNG, List.of(places), 300, 0.7);
    }

    @Test
    void 이름이_같고_가까우면_같은_가게다() {
        Decision decision = decide("鮨いしかわ", place("p1", "鮨いしかわ", 10));

        assertThat(decision.status()).isEqualTo(GoogleMatchStatus.MATCHED);
        assertThat(decision.placeId()).isEqualTo("p1");
    }

    @Test
    void 띄어쓰기_전각_앞뒤에_붙은_말은_무시한다() {
        assertThat(decide("トレフミヤモト", place("p", "トレフ ミヤモト", 4)).status()).isEqualTo(GoogleMatchStatus.MATCHED);
        assertThat(decide("うりんぼ", place("p", "博多ラーメン うりんぼ", 6)).status()).isEqualTo(GoogleMatchStatus.MATCHED);
        assertThat(decide("バンコクスパイス 新宿店", place("p", "バンコクスパイス新宿", 2)).status())
                .isEqualTo(GoogleMatchStatus.MATCHED);
        assertThat(decide("中国料理 星ヶ岡", place("p", "中国料理「星ヶ岡」", 26)).status())
                .isEqualTo(GoogleMatchStatus.MATCHED);
    }

    @Test
    void 역_지하상가처럼_좌표가_어긋나도_300m_안에서_이름이_맞으면_같은_가게다() {
        Decision decision = decide("Japanese Malt Whisky SAKURA グランスタ東京店",
                place("p", "Japanese Malt Whisky SAKURA グランスタ東京店", 262));

        assertThat(decision.status()).isEqualTo(GoogleMatchStatus.MATCHED);
    }

    @Test
    void 지점_이름이_다르면_가깝고_이름이_비슷해도_다른_가게다() {
        Decision decision = decide("ハンバーグ&ステーキ食堂クラウゾ 本店",
                place("p", "ハンバーグ＆ステーキ食堂クラウゾ 2号店", 88));

        assertThat(decision.status()).isEqualTo(GoogleMatchStatus.NOT_FOUND);
        assertThat(decision.placeId()).isNull();
    }

    @Test
    void 지점_이름이_한쪽에_포함되면_같은_지점으로_본다() {
        Decision decision = decide("恋とスパイス 新宿中村屋 グランスタ東京店",
                place("p", "恋とスパイス 新宿中村屋 グランスタ東京", 189));

        assertThat(decision.status()).isEqualTo(GoogleMatchStatus.MATCHED);
    }

    @Test
    void 같은_자리라도_이름이_전혀_다르면_폐업한_자리의_다른_가게로_보고_찾지_못함() {
        Decision decision = decide("レストラン鳴海", place("p", "ベンガル家庭料理 トルカリ 神保町本店", 8));

        assertThat(decision.status()).isEqualTo(GoogleMatchStatus.NOT_FOUND);
    }

    @Test
    void 이름이_맞아도_300m보다_멀면_다른_지점이다() {
        assertThat(decide("満寿家", place("p", "満寿家", 400)).status()).isEqualTo(GoogleMatchStatus.NOT_FOUND);
    }

    @Test
    void 검색_결과가_없으면_찾지_못함() {
        assertThat(decide("満寿家").status()).isEqualTo(GoogleMatchStatus.NOT_FOUND);
    }

    @Test
    void 검색_순위보다_이름이_맞는_결과를_고른다() {
        Decision decision = decide("日本橋だし場",
                place("wrong1", "にんべん 日本橋本店", 5),
                place("wrong2", "日本橋 室町", 7),
                place("right", "日本橋だし場", 9));

        assertThat(decision.placeId()).isEqualTo("right");
    }

    @Test
    void 이름이_똑같이_맞으면_더_가까운_곳() {
        Decision decision = decide("満寿家", place("far", "満寿家", 250), place("near", "満寿家", 20));

        assertThat(decision.placeId()).isEqualTo("near");
    }

    @Test
    void 같은_가게가_영업_종료로_나오면_폐업() {
        Decision decision = decide("鮨いしかわ", place("p1", "鮨いしかわ", 10, "CLOSED_PERMANENTLY"));

        assertThat(decision.status()).isEqualTo(GoogleMatchStatus.CLOSED);
        assertThat(decision.placeId()).isEqualTo("p1");
    }

    @Test
    void 임시_휴업은_폐업으로_보지_않는다() {
        Decision decision = decide("鮨いしかわ", place("p1", "鮨いしかわ", 10, "CLOSED_TEMPORARILY"));

        assertThat(decision.status()).isEqualTo(GoogleMatchStatus.MATCHED);
    }

    @Test
    void 좌표나_이름이_빠진_결과는_건너뛴다() {
        SearchPlace noLocation = new SearchPlace("x", new LocalizedText("鮨いしかわ", "ja"), null, "OPERATIONAL");
        SearchPlace noName = new SearchPlace("y", null, new LatLng(LAT, LNG), "OPERATIONAL");

        assertThat(decide("鮨いしかわ", noLocation, noName).status()).isEqualTo(GoogleMatchStatus.NOT_FOUND);
    }

    @Test
    void 영어_이름_가게는_놓친다_알려진_한계() {
        // 가까운 다른 이름 가게(폐업한 자리)를 받지 않기 위한 대가. 화면에는 "찾지 못했어요"로 보인다
        assertThat(decide("ザ グレートバーガー", place("p", "THE GREAT BURGER", 4)).status())
                .isEqualTo(GoogleMatchStatus.NOT_FOUND);
    }

    @Test
    void 이름_비슷한_정도() {
        assertThat(RestaurantPlaceMatcher.nameSimilarity("鳥酎 飯田橋", "鳥酎 飯田橋店")).isEqualTo(1.0);
        assertThat(RestaurantPlaceMatcher.nameSimilarity("平川", "すし匠")).isZero();
        assertThat(RestaurantPlaceMatcher.nameSimilarity("", "すし匠")).isZero();
        assertThat(RestaurantPlaceMatcher.nameSimilarity("笹八 グランスタ店", "ゐざさ茶屋 グランスタ東京店")).isLessThan(0.7);
    }

    @Test
    void 지점_이름은_띄어쓴_마지막_말이_店으로_끝날_때만() {
        assertThat(RestaurantPlaceMatcher.branchOf("クラウゾ 本店")).isEqualTo("本");
        assertThat(RestaurantPlaceMatcher.branchOf("できたて屋 コモレ四谷店")).isEqualTo("コモレ四谷");
        assertThat(RestaurantPlaceMatcher.branchOf("揚州飯店")).isNull();
        assertThat(RestaurantPlaceMatcher.branchOf("やきとり嶋家 南麻布")).isNull();
    }
}
