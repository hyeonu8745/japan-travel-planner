package com.example.japantravelplanner.restaurant.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 체인점 브랜드 장르(한국어) → 타베로그식 장르, 예산 */
class ChainGenresTest {

    // 11-16 가져온 브랜드 86개의 장르 전부 (chain_brands.genre_ko)
    private static final List<String> ALL = List.of(
            "애니 콜라보 카페", "카페", "카페(킷사텐)", "도넛", "버블티", "케이크·양과자", "타코야키(간식)",
            "규동", "규동·우동", "나가사키 짬뽕", "대만요리(소룡포)", "돈카츠·가츠동", "라멘", "라멘·중화", "샌드위치",
            "샤부샤부", "샤부샤부 뷔페", "샤부샤부·일식", "소바", "스시", "스테이크", "아메리칸", "야키니쿠",
            "야키니쿠 무한리필", "우동", "이탈리안", "일식", "정식", "중화요리", "중화요리·교자", "철판 스테이크·라이스",
            "치킨", "카레", "텐동", "파스타", "패밀리 레스토랑", "패밀리 레스토랑(이탈리안)", "함박스테이크", "햄버거", "회전초밥");

    @Test
    void 지금_브랜드의_장르는_모두_일본어_장르가_있다() {
        assertThat(ALL).allSatisfy(genre -> assertThat(ChainGenres.of(genre)).as(genre).isNotBlank());
    }

    @Test
    void 모르는_장르는_null() {
        assertThat(ChainGenres.of("없는 장르")).isNull();
        assertThat(ChainGenres.of(null)).isNull();
    }

    @Test
    void 체인점_지점은_브랜드_장르와_예산을_쓴다() {
        ChainBrand yoshinoya = ChainBrand.builder().name("吉野家").category(ChainCategory.MEAL).genreKo("규동")
                .budgetMin(500).budgetMax(1000).build();
        Restaurant store = Restaurant.builder().name("吉野家 新宿店").brand(yoshinoya).build();

        assertThat(store.isChain()).isTrue();
        assertThat(store.genreOrBrand()).isEqualTo("牛丼");
        assertThat(store.lunchMax()).isEqualTo(1000);
        assertThat(store.dinnerMin()).isEqualTo(500);
    }

    @Test
    void 타베로그_식당은_자기_장르와_예산을_쓴다() {
        Restaurant r = Restaurant.builder().name("洋食屋").genre("洋食").budgetLunchMax(1500).build();

        assertThat(r.isChain()).isFalse();
        assertThat(r.genreOrBrand()).isEqualTo("洋食");
        assertThat(r.lunchMax()).isEqualTo(1500);
        assertThat(r.dinnerMax()).isNull();
    }
}
