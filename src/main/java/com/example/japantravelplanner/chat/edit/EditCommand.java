package com.example.japantravelplanner.chat.edit;

import com.fasterxml.jackson.annotation.JsonProperty;

/** AI가 사용자의 말을 바꾼 명령 (EditPrompt). 서버가 다시 검증한다 */
record EditCommand(
        String intent,
        @JsonProperty("stop_ref") String stopRef,
        @JsonProperty("after_ref") String afterRef,
        Integer day,
        String target,
        @JsonProperty("choice_index") Integer choiceIndex,
        @JsonProperty("clarify_question") String clarifyQuestion,
        String reply
) {

    static EditCommand of(String intent) {
        return new EditCommand(intent, null, null, null, null, null, null, "");
    }

    static EditCommand choose(int index) {
        return new EditCommand("CHOOSE", null, null, null, null, index, null, "");
    }
}
