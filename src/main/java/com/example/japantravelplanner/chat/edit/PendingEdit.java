package com.example.japantravelplanner.chat.edit;

import java.util.List;

/**
 * "이렇게 바꿀까요?" / "어느 곳으로 할까요?"라고 물은 뒤 답을 기다리는 변경 (ChatMessage.pendingEdit에 JSON으로 저장).
 * 일정 버전이 그 사이에 바뀌면 적용하지 않는다 (항목 id가 버전마다 다르다).
 *
 * @param kind     REPLACE_MEAL, REPLACE_ATTRACTION, DELETE, MOVE, ADD
 * @param version  물어볼 때의 일정 버전
 * @param stopId   바꾸거나 빼거나 옮길 항목 (ADD는 null)
 * @param day      날짜 번호
 * @param position ADD에서 넣을 자리 (0부터)
 * @param stopIds  MOVE에서 바꾼 뒤의 그날 항목 순서
 * @param options  고를 수 있는 곳 (REPLACE·ADD). 하나면 "바꿀까요?", 여럿이면 "어느 곳으로 할까요?"
 * @param before   바꾸기 전 이름 (안내 문장용)
 */
record PendingEdit(String kind, int version, Long stopId, Integer day, Integer position, List<Long> stopIds,
                   List<Option> options, String before) {

    static final String REPLACE_MEAL = "REPLACE_MEAL";
    static final String REPLACE_ATTRACTION = "REPLACE_ATTRACTION";
    static final String DELETE = "DELETE";
    static final String MOVE = "MOVE";
    static final String ADD = "ADD";

    /** @param id 식당 또는 관광지 id */
    record Option(Long id, String label) {
    }

    boolean needsChoice() {
        return options != null && options.size() > 1;
    }
}
