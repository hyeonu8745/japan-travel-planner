package com.example.japantravelplanner.global.exception;

/** 요청한 자원이 없거나, 접근 권한이 없어 없는 것으로 취급하는 경우 → 404 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}