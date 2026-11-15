package com.example.japantravelplanner.global.exception;

/** Google 등 외부 API 장애 → 503 */
public class ExternalApiException extends RuntimeException {

    /**
     * @param message 사용자에게 보여 줄 문구
     * @param cause 원래 오류 (로그용)
     */
    public ExternalApiException(String message, Throwable cause) {
        super(message, cause);
    }
}