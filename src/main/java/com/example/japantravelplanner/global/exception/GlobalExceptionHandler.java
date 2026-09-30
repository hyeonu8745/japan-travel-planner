package com.example.japantravelplanner.global.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 비즈니스 검증 실패 → 400
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage(), "잘못된 요청입니다.");
    }

    // 입력값 검증 실패(@Valid), 쿼리 파라미터 타입 변환 실패 → 400
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Map<String, String>> handleBindException(BindException e) {
        FieldError error = e.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);

        String message;
        if (error == null) {
            message = "요청 값이 올바르지 않습니다.";
        } else if (error.isBindingFailure()) {
            // 타입 변환 실패 (예: sort=abc)
            message = error.getField() + " 값이 올바르지 않습니다: " + error.getRejectedValue();
        } else {
            // 검증 어노테이션 실패: 어노테이션에 적은 메시지 사용
            message = error.getDefaultMessage();
        }
        return error(HttpStatus.BAD_REQUEST, message, "요청 값이 올바르지 않습니다.");
    }

    // 요청 본문이 없거나 JSON 문법·날짜·선택 값이 잘못된 경우 → 400
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleNotReadable(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST,
                "요청 본문 형식이 올바르지 않습니다. (JSON 문법, 날짜 형식, 선택 값을 확인해주세요)", null);
    }

    // 자원 없음 → 404
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage(), "찾을 수 없습니다.");
    }

    // 중복 등 충돌 → 409
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(ConflictException e) {
        return error(HttpStatus.CONFLICT, e.getMessage(), "이미 존재합니다.");
    }

    /** Map.of는 null을 허용하지 않으므로, 메시지가 없으면 기본 문구로 대체 */
    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message, String fallback) {
        String body = Objects.requireNonNullElse(message, Objects.requireNonNullElse(fallback, "오류가 발생했습니다."));
        return ResponseEntity.status(status).body(Map.of("message", body));
    }

    // 외부 AI 서비스 장애 → 503
    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<Map<String, String>> handleAiService(AiServiceException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), "AI 서비스에 일시적인 문제가 있습니다.");
    }

    // 호출 제한 초과 → 429
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<Map<String, String>> handleTooManyRequests(TooManyRequestsException e) {
        return error(HttpStatus.TOO_MANY_REQUESTS, e.getMessage(), "요청이 너무 많습니다.");
    }
}