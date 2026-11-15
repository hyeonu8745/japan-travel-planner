package com.example.japantravelplanner.global.exception;

/** 이미 존재하는 자원과 충돌하는 경우 (예: 중복 이메일) → 409 */
public class ConflictException extends RuntimeException {

    /** @param message 사용자에게 보여 줄 문구 */
    public ConflictException(String message) {
        super(message);
    }
}