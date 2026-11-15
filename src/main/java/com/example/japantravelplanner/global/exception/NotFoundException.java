package com.example.japantravelplanner.global.exception;

/** 요청한 자원이 없거나, 접근 권한이 없어 없는 것으로 취급하는 경우 → 404 */
public class NotFoundException extends RuntimeException {

    /** @param message 사용자에게 보여 줄 문구 */
    public NotFoundException(String message) {
        super(message);
    }
}