package com.example.japantravelplanner.chat.service;

import com.example.japantravelplanner.trip.entity.Interest;
import com.example.japantravelplanner.trip.entity.Trip;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 첫 인사와 대화를 끊지 않기 (10-10 화면에서 발견: AI가 먼저 묻지 않고, 첫 답에서 대화를 끝냄) */
class ChatOpeningTest {

    @Test
    void 첫_인사는_여행_기간과_관심사를_짚고_음식_질문으로_끝난다() {
        Trip trip = Trip.builder().startDate(LocalDate.of(2026, 10, 12)).endDate(LocalDate.of(2026, 10, 18))
                .interests(List.of(Interest.FOOD, Interest.NIGHT_VIEW)).build();

        String greeting = ChatGreeting.of(trip);

        assertThat(greeting).startsWith("안녕하세요! TabiMate예요. 6박 7일 도쿄 여행")
                .contains("관심사(현지 음식, 야경)")
                .endsWith(ChatGreeting.FIRST_QUESTION);
    }

    @Test
    void 관심사가_없으면_관심사_문장_없이_인사한다() {
        Trip trip = Trip.builder().startDate(LocalDate.of(2026, 10, 12)).endDate(LocalDate.of(2026, 10, 13)).build();

        assertThat(ChatGreeting.of(trip)).contains("1박 2일").doesNotContain("관심사")
                .endsWith(ChatGreeting.FIRST_QUESTION);
    }

    @Test
    void 준비_전인데_질문_없이_끝나면_다음_질문을_붙인다() {
        String reply = "조용한 분위기를 선호하시는군요. 취향이 잘 전달되었습니다!";

        assertThat(ReplyFlow.keepGoing(reply, false, false))
                .isEqualTo(reply + "\n\n" + ReplyFlow.FOLLOW_UP);
    }

    @Test
    void 질문으로_끝나거나_준비가_됐거나_생성을_원하면_그대로() {
        String question = "조용한 곳 위주로 볼게요. 첫날 숙소에는 몇 시쯤 도착하세요?";
        String done = "알겠습니다. 일정을 만들어드릴게요.";

        assertThat(ReplyFlow.keepGoing(question, false, false)).isEqualTo(question);
        assertThat(ReplyFlow.keepGoing(done, true, false)).isEqualTo(done);
        assertThat(ReplyFlow.keepGoing(done, false, true)).isEqualTo(done);
    }

    @Test
    void 전각_물음표로_끝나도_질문으로_본다() {
        String reply = "좋아요. 첫날 몇 시쯤 도착하세요？";

        assertThat(ReplyFlow.keepGoing(reply, false, false)).isEqualTo(reply);
    }
}
