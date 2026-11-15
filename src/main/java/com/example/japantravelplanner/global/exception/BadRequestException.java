package com.example.japantravelplanner.global.exception;

/**
 * 사용자에게 그대로 보여 줄 400 오류 (11-15 보안 1-4).
 * 응답에는 이 예외의 문구만 나간다. 라이브러리가 던진 IllegalArgumentException(NumberFormatException 등)은
 * 내부 문구가 새지 않게 "잘못된 요청입니다." 로 바꾼다 (GlobalExceptionHandler).
 * IllegalArgumentException 을 상속해서, 예전처럼 IllegalArgumentException 으로 잡는 곳은 그대로 동작한다.
 */
public class BadRequestException extends IllegalArgumentException {

    public BadRequestException(String message) {
        super(message);
    }
}
