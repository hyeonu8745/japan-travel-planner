package com.example.japantravelplanner.chat.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

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

    public boolean isComplex() {
        return "HIGH".equals(complexity);
    }
}