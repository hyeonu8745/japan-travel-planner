package com.example.japantravelplanner.itinerary.meal;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GenreGroupsTest {

    @Test
    void 라멘과_츠케멘은_같은_묶음() {
        assertThat(GenreGroups.groupsOf(Set.of("ラーメン"))).containsExactly("RAMEN");
        assertThat(GenreGroups.groupsOf(Set.of("つけ麺"))).containsExactly("RAMEN");
        assertThat(GenreGroups.groupsOf(Set.of("油そば", "餃子"))).containsExactly("RAMEN");
    }

    @Test
    void 다른_음식은_묶음이_없다() {
        assertThat(GenreGroups.groupsOf(Set.of("焼き鳥"))).isEmpty();
        assertThat(GenreGroups.groupsOf(Set.of("そば", "うどん"))).isEmpty();
        assertThat(GenreGroups.groupsOf(Set.of())).isEmpty();
    }

    @Test
    void 일본식_카레와_인도_카레는_같은_묶음() {
        // 실제 DAY2: 점심 カレーライス → 저녁 インド料理,インドカレー (이름이 달라서 반복으로 안 잡혔다)
        assertThat(GenreGroups.groupsOf(Set.of("カレーライス"))).containsExactly("CURRY");
        assertThat(GenreGroups.groupsOf(Set.of("インド料理", "インドカレー", "バイキング"))).containsExactly("CURRY");
        assertThat(GenreGroups.groupsOf(Set.of("スープカレー"))).containsExactly("CURRY");
    }

    @Test
    void 카레_장르가_없는_인도_식당은_묶음이_아니다() {
        assertThat(GenreGroups.groupsOf(Set.of("インド料理"))).isEmpty();
    }
}