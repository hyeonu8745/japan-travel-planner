package com.example.japantravelplanner.global.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 11-15 보안 1-4: 우리 문구만 보여 주고 내부 오류 문구는 숨긴다 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void 우리가_쓴_400_문구는_그대로() {
        var res = handler.handleBadRequest(new BadRequestException("이미 이 순서예요."));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).containsEntry("message", "이미 이 순서예요.");
    }

    @Test
    void 라이브러리_예외의_문구는_숨긴다() {
        var res = handler.handleIllegalArgument(new NumberFormatException("For input string: \"abc\""));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).containsEntry("message", "잘못된 요청입니다.");
    }

    @Test
    void 처리하지_못한_예외는_500_일반_문구() throws Exception {
        var res = handler.handleUnexpected(new IllegalStateException("org.postgresql... 내부 정보"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody()).containsEntry("message", "일시적인 문제가 발생했어요. 잠시 뒤에 다시 시도해주세요.");
    }

    @Test
    void 상태_코드가_정해진_Spring_예외는_원래대로_넘긴다() {
        var e = new ResponseStatusException(HttpStatus.NOT_FOUND);
        assertThatThrownBy(() -> handler.handleUnexpected(e)).isSameAs(e);
        var typeMismatch = new org.springframework.beans.TypeMismatchException("abc", Integer.class);
        assertThatThrownBy(() -> handler.handleUnexpected(typeMismatch)).isSameAs(typeMismatch);
    }

    @Test
    void 예전처럼_IllegalArgumentException_으로_잡는_곳도_동작한다() {
        assertThat(new BadRequestException("x")).isInstanceOf(IllegalArgumentException.class);
    }
}
