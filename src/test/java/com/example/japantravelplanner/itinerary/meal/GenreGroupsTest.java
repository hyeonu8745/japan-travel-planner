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
}