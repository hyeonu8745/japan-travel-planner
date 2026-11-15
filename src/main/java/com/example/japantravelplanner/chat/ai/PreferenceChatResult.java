package com.example.japantravelplanner.chat.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 취향 대화에서 AI 가 돌려준 결과: 정리된 조건 전체, 요약, 다음 답, 일정 만들 준비가 됐는지, 요청이 복잡한지(복잡하면 다음 호출에 고급 모델).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PreferenceChatResult(
        @JsonProperty("reply_to_user") String replyToUser,
        List<ConstraintItem> constraints,
        @JsonProperty("arrival_time") String arrivalTime,
        @JsonProperty("departure_time") String departureTime,
        @JsonProperty("food_budget_ratio") Double foodBudgetRatio,
        String summary,
        @JsonProperty("ready_to_generate") boolean readyToGenerate,
        @JsonProperty("user_wants_generation") boolean userWantsGeneration,
        String complexity,
        @JsonProperty("complexity_reason") String complexityReason,
        @JsonProperty("mentioned_travelers") Integer mentionedTravelers   // ← 추가
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ConstraintItem(String category, String description) {
    }

    /** AI 가 요청을 복잡하다고 봤는지 (다음 호출은 고급 모델) */
    public boolean isComplex() {
        return "HIGH".equals(complexity);
    }
}