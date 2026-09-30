package com.example.japantravelplanner.global.exception;

/** 호출 제한 초과 → 429 */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}