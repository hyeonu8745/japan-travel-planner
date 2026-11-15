package com.example.japantravelplanner.global.exception;

/** 외부 AI 서비스 호출 실패 → 503 */
public class AiServiceException extends RuntimeException {

    /**
     * @param message 사용자에게 보여 줄 문구
     * @param cause 원래 오류 (로그용)
     */
    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}