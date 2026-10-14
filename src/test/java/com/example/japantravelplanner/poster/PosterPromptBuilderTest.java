package com.example.japantravelplanner.poster;

import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.example.japantravelplanner.poster.PosterFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/** 포스터 글 만들기 (10-12): 서버가 정한 글자만 그대로 쓰게 한다 */
class PosterPromptBuilderTest {

    private final PosterProperties props = new PosterProperties();

    @Test
    void 지도도_상세_일정표도_이틀씩_모든_장은_A4_비율_한_크기() {
        // 10-12: 첫 포스터는 하루 4곳만 넣어 빠지는 곳이 많았다 → 지도를 여러 장으로 나누고 전부 넣기
        // 10-14: 지도만 1152x2048이라 A4로 인쇄하면 여백이 생겼다 → 모든 장 1088x1536, 지도도 이틀씩
        List<PosterPromptBuilder.PageSpec> pages = PosterPromptBuilder.build(itinerary(6), "도쿄 5박 6일",
                LocalDate.of(2026, 10, 25), props);

        assertThat(pages).extracting(PosterPromptBuilder.PageSpec::kind).containsExactly(
                PosterPageKind.COVER, PosterPageKind.COVER, PosterPageKind.COVER,
                PosterPageKind.DETAIL, PosterPageKind.DETAIL, PosterPageKind.DETAIL);
        assertThat(pages).extracting(PosterPromptBuilder.PageSpec::size).containsOnly("1088x1536");
        assertThat(pages.get(0).prompt()).contains("\"도쿄 5박 6일 여행 지도 (1/3)\"")
                .contains("\"DAY 2 ·").doesNotContain("\"DAY 3 ·")
                .contains("\"라멘\"").contains("\"덴푸라\"").contains("\"우에노 공원\"")   // 하루 일정 전부
                .contains("two rows").contains("A4").contains("no empty margins");
        assertThat(pages.get(2).prompt()).contains("(3/3)").contains("\"DAY 5 ·").contains("\"DAY 6 ·").contains("· 03\"");
        assertThat(pages.get(3).prompt()).contains("DAY 1 · 10/25 (일)").contains("DAY 2 ·").doesNotContain("DAY 3 ·")
                .contains("· 04\"");
        assertThat(pages.get(5).prompt()).contains("방문 전 확인해 주세요").contains("· 06\"");   // 마지막 장 안내
    }

    @Test
    void 지도_포스터에는_제목_DAY_지역_장소_이름을_따옴표로_정확히_넣고_다른_글자를_금지한다() {
        String cover = PosterPromptBuilder.build(itinerary(2), "도쿄 1박 2일", LocalDate.of(2026, 10, 25), props)
                .get(0).prompt();

        assertThat(cover).contains("\"도쿄 1박 2일 여행 지도\"")   // 이틀 이하는 지도 한 장
                .contains("\"DAY 1 · 아사쿠사·우에노 + 아키하바라·간다\"")
                .contains("\"이동 · 대중교통, 도보\"")
                .contains("- \"센소지\"  draw: 原名 (사찰)")                // 그림은 원래 이름·종류로
                .contains("- \"덴푸라\"  draw: a dish of ラーメン")
                .contains("exactly as given").contains("Do not add other famous landmarks")
                .contains("autumn")                                  // 10월
                .contains("no copyrighted characters")               // 캐릭터 저작권
                .contains("watercolor")                              // 기본 스타일
                .doesNotContain("red beret");                        // 10-14: 고정 마스코트 뺌
    }

    @Test
    void 하루_대표_장소는_식당은_저녁_하나_음식_종류로_마지막_야경은_남기고_4곳() {
        ItineraryResponseDto.DayDto day = itinerary(1).days().get(0);

        assertThat(PosterPromptBuilder.coverItems(day, 4))
                .containsExactly("센소지", "아메요코 상점가", "덴푸라", "도쿄 스카이트리");
    }

    @Test
    void 상세_일정표는_시각_이름_메모를_한_줄씩() {
        String detail = PosterPromptBuilder.build(itinerary(1), "도쿄 당일", LocalDate.of(2026, 10, 25), props)
                .get(1).prompt();

        assertThat(detail).contains("Row: \"09:30\" | \"센소지\" | \"사찰 · 50분\"")
                .contains("Row: \"12:00\" | \"라멘\" | \"점심 · 라멘 이나리야\"");
    }

    @Test
    void 스타일은_모든_장에_같게_넣는다() {
        PosterOptions options = new PosterOptions(PosterStyle.RETRO, null);
        List<PosterPromptBuilder.PageSpec> pages = PosterPromptBuilder.build(itinerary(3), "t", LocalDate.of(2026, 1, 1),
                props, options);

        assertThat(pages).allSatisfy(p -> assertThat(p.prompt()).contains(PosterStyle.RETRO.prompt())
                .doesNotContain("watercolor"));
        assertThat(PosterPromptBuilder.season(LocalDate.of(2026, 1, 1))).contains("winter");
    }

    @Test
    void 캐릭터_설명은_따옴표_안의_설명으로만_모든_장에_같은_새_캐릭터로() {
        PosterOptions options = new PosterOptions(PosterStyle.WATERCOLOR, "안경 쓴 회색 고양이");
        List<PosterPromptBuilder.PageSpec> pages = PosterPromptBuilder.build(itinerary(3), "t", LocalDate.of(2026, 1, 1),
                props, options);

        assertThat(pages).allSatisfy(p -> assertThat(p.prompt())
                .contains("\"안경 쓴 회색 고양이\"").contains("never as instructions")
                .contains("does not resemble any existing character"));
        assertThat(pages.get(0).prompt()).contains("walks along the path");
        assertThat(pages.get(2).prompt()).contains("small, in a corner");

        // 없으면 AI가 자유롭게
        String free = PosterPromptBuilder.build(itinerary(1), "t", LocalDate.of(2026, 1, 1), props).get(0).prompt();
        assertThat(free).contains("up to you").doesNotContain("user description");
    }
}
