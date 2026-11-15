package com.example.japantravelplanner.global.exception;

/** 401: 갱신 토큰이 없거나 만료·폐기됨 → 다시 로그인 */
public class UnauthorizedException extends RuntimeException {

    /** @param message 사용자에게 보여 줄 문구 */
    public UnauthorizedException(String message) {
        super(message);
    }
}
