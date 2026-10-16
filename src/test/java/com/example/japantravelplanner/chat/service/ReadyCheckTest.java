package com.example.japantravelplanner.chat.service;

import com.example.japantravelplanner.trip.entity.ConstraintCategory;
import com.example.japantravelplanner.trip.entity.PreferenceConstraint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 10-16: AI가 준비를 미뤄도 충분히 들었으면 서버가 생성 준비로 넘긴다 */
class ReadyCheckTest {

    private static PreferenceConstraint c(ConstraintCategory category, String text) {
        return new PreferenceConstraint(category, text);
    }

    private final List<PreferenceConstraint> foodAndFeel = List.of(
            c(ConstraintCategory.FOOD_PREFER, "장어덮밥"), c(ConstraintCategory.ATMOSPHERE, "조용한 곳"));

    @Test
    void 음식과_분위기를_들었으면_네_번째_답부터_준비() {
        assertThat(ReadyCheck.enough(3, foodAndFeel)).isFalse();
        assertThat(ReadyCheck.enough(4, foodAndFeel)).isTrue();
    }

    @Test
    void 음식이나_분위기_이동_중_하나가_없으면_네_번째에는_아직() {
        assertThat(ReadyCheck.enough(5, List.of(c(ConstraintCategory.FOOD_AVOID, "라멘")))).isFalse();
        assertThat(ReadyCheck.enough(5, List.of(c(ConstraintCategory.MOBILITY, "적당히 걷기")))).isFalse();
    }

    @Test
    void 일곱_번째_답이면_무엇을_들었든_준비() {
        assertThat(ReadyCheck.enough(7, List.of())).isTrue();
    }
}
