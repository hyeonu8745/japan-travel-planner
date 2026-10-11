package com.example.japantravelplanner.itinerary.naming;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GenreLabelsTest {

    @Test
    void 여행자가_부르는_이름으로_바꾼다() {
        // 사용자 의견 10-10: とんかつ → 돈카츠 (돈카쓰·돈가스 아님), 츠케멘·야키토리처럼 익숙한 표기
        assertThat(GenreLabels.of("とんかつ")).containsExactly("돈카츠");
        assertThat(GenreLabels.of("つけ麺,ラーメン")).containsExactly("츠케멘", "라멘");
        assertThat(GenreLabels.of("焼鳥,居酒屋,日本酒バー")).containsExactly("야키토리", "이자카야", "사케 바");
    }

    @Test
    void 같은_이름은_한_번만_모르는_장르는_원문_그대로() {
        assertThat(GenreLabels.of("日本料理,和食（その他）")).containsExactly("일식");
        assertThat(GenreLabels.of("新しいジャンル")).containsExactly("新しいジャンル");
        assertThat(GenreLabels.of(null)).isEmpty();
        assertThat(GenreLabels.joined("")).isNull();
        assertThat(GenreLabels.joined("寿司,海鮮丼")).isEqualTo("스시·카이센동");
    }

    @Test
    void 데이터에_많은_장르는_모두_이름표에_있다() {
        // 데이터(식당 9,399곳)에서 많이 나오는 장르 위 20개
        List<String> top = List.of("居酒屋", "日本料理", "イタリアン", "魚介料理・海鮮料理", "カフェ", "ラーメン", "ワインバー",
                "フレンチ", "中華料理", "焼肉", "寿司", "ダイニングバー", "ビストロ", "焼鳥", "そば", "ステーキ",
                "和食（その他）", "ケーキ", "パスタ", "つけ麺");
        assertThat(top).allMatch(GenreLabels::has);
    }
}
