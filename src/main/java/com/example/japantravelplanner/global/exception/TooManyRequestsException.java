package com.example.japantravelplanner.global.exception;

/** 호출 제한 초과 → 429 */
public class TooManyRequestsException extends RuntimeException {

    /** @param message 사용자에게 보여 줄 문구 (언제 다시 할 수 있는지 등) */
    public TooManyRequestsException(String message) {
        super(message);
    }
}