package com.example.japantravelplanner.chat.service;

import com.example.japantravelplanner.trip.entity.ConstraintCategory;
import com.example.japantravelplanner.trip.entity.PreferenceConstraint;

import java.util.List;
import java.util.Set;

/**
 * 취향 대화를 서버가 끝낼 때 (10-16).
 * AI가 ready_to_generate를 계속 false로 두고 이미 들은 분위기·음식을 다시 묻는 바람에
 * 14번을 답해도 "일정을 만들어드릴까요?"가 나오지 않은 적이 있다 (여행 조건 4박 5일, 프론트 테스트).
 * 그래서 AI 판단과 별도로, 이만큼 들었으면 서버가 생성 준비로 넘긴다. 도착·출발 시각은 몰라도 기본값으로 만든다.
 */
final class ReadyCheck {

    /** 음식과 분위기·이동을 다 들었으면 이 횟수부터 준비 */
    static final int ENOUGH_TURNS = 4;
    /** 무엇을 들었든 이 횟수가 되면 준비 ("없어요"만 답한 경우 등) */
    static final int MAX_TURNS = 7;

    private static final Set<ConstraintCategory> FOOD = Set.of(ConstraintCategory.FOOD_PREFER, ConstraintCategory.FOOD_AVOID);
    private static final Set<ConstraintCategory> FEEL = Set.of(ConstraintCategory.ATMOSPHERE, ConstraintCategory.MOBILITY);

    private ReadyCheck() {
    }

    /**
     * @param userTurns   이번 메시지를 포함해 사용자가 보낸 메시지 수
     * @param constraints 이번 답까지 정리된 취향
     */
    static boolean enough(long userTurns, List<PreferenceConstraint> constraints) {
        if (userTurns >= MAX_TURNS) return true;
        boolean food = constraints.stream().anyMatch(c -> FOOD.contains(c.getCategory()));
        boolean feel = constraints.stream().anyMatch(c -> FEEL.contains(c.getCategory()));
        return userTurns >= ENOUGH_TURNS && food && feel;
    }
}
