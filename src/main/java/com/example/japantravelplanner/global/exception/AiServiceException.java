package com.example.japantravelplanner.global.exception;

/** 외부 AI 서비스 호출 실패 → 503 */
public class AiServiceException extends RuntimeException {

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}