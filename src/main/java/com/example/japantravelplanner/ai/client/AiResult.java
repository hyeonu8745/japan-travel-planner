package com.example.japantravelplanner.ai.client;

/** AI 호출 결과. 토큰 수는 비용 추적과 나중의 모니터링에 사용 */
public record AiResult(
        String text,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        Integer reasoningTokens,
        long elapsedMs
) {
}