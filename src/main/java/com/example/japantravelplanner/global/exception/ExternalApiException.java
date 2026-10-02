package com.example.japantravelplanner.global.exception;

/** Google 등 외부 API 장애 → 503 */
public class ExternalApiException extends RuntimeException {

    public ExternalApiException(String message, Throwable cause) {
        super(message, cause);
    }
}