package com.example.japantravelplanner.global.exception;

/** 401: 갱신 토큰이 없거나 만료·폐기됨 → 다시 로그인 (11-15) */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
