package com.example.japantravelplanner.ai.client;

import com.example.japantravelplanner.ai.config.OpenAiProperties;
import com.example.japantravelplanner.global.exception.AiServiceException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OpenAI Responses API 호출 전담 클래스.
 * 모델 선택, 요청 조립, 응답에서 텍스트 추출, 실패 처리, 사용량 로그를 한 곳에서 담당한다.
 */
@Slf4j
@Component
public class OpenAiClient {

    private final OpenAiProperties properties;
    private final RestClient restClient;

    public OpenAiClient(OpenAiProperties properties) {
        this.properties = properties;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));

        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .build();
    }

    /** 자유 텍스트로 답변 받기 */
    public AiResult generate(AiModelTier tier, String instructions, String input) {
        return call(tier, instructions, input, null, properties.getMaxOutputTokens(), null);
    }

    public AiResult generateJson(AiModelTier tier, String instructions, String input, Map<String, Object> format) {
        return call(tier, instructions, input, format, properties.getMaxOutputTokens(), null);
    }

    /**
     * 출력이 긴 작업(여러 날 설명 등)용: 최대 출력 토큰을 직접 지정한다.
     * 추론 모델은 추론에 쓴 토큰도 이 한도에 포함되므로 넉넉하게 준다
     */
    public AiResult generateJson(AiModelTier tier, String instructions, String input,
                                 Map<String, Object> format, int maxOutputTokens) {
        return call(tier, instructions, input, format, maxOutputTokens, null);
    }

    /**
     * 같은 입력이면 같은 결과가 나와야 하는 작업(리뷰 분류 등)용: temperature를 지정한다.
     * 추론 모델(ADVANCED)은 temperature를 받지 않아서 BASIC일 때만 보낸다
     */
    public AiResult generateJson(AiModelTier tier, String instructions, String input,
                                 Map<String, Object> format, int maxOutputTokens, double temperature) {
        return call(tier, instructions, input, format, maxOutputTokens, temperature);
    }

    private AiResult call(AiModelTier tier, String instructions, String input,
                          Map<String, Object> format, int maxOutputTokens, Double temperature) {
        boolean advanced = tier == AiModelTier.ADVANCED;
        String model = advanced ? properties.getAdvancedModel() : properties.getBasicModel();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("instructions", instructions);
        body.put("input", input);
        body.put("max_output_tokens", maxOutputTokens);
        if (advanced) {
            // 추론 모델에만 추론 강도를 보냄 (4o mini에 보내면 에러)
            body.put("reasoning", Map.of("effort", properties.getAdvancedReasoningEffort()));
        }
        if (format != null) {
            body.put("text", Map.of("format", format));
        }
        if (temperature != null && !advanced) {
            body.put("temperature", temperature);
        }

        long start = System.currentTimeMillis();
        ResponsesApiResponse response;
        try {
            response = restClient.post()
                    .uri("/responses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(ResponsesApiResponse.class);
        } catch (RestClientResponseException e) {
            log.error("[OpenAI] 호출 실패 model={} status={} body={}",
                    model, e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new AiServiceException("AI 응답을 받지 못했습니다. 잠시 후 다시 시도해주세요.", e);
        } catch (RestClientException e) {
            log.error("[OpenAI] 연결 실패 model={} message={}", model, e.getMessage());
            throw new AiServiceException("AI 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.", e);
        }
        long elapsedMs = System.currentTimeMillis() - start;

        String text = extractText(response);
        Usage usage = response == null ? null : response.usage();
        Integer inputTokens = usage == null ? null : usage.inputTokens();
        Integer outputTokens = usage == null ? null : usage.outputTokens();
        Integer reasoningTokens = (usage == null || usage.outputTokensDetails() == null)
                ? null : usage.outputTokensDetails().reasoningTokens();

        log.info("[OpenAI] model={} status={} elapsedMs={} inputTokens={} outputTokens={} reasoningTokens={}",
                model, response == null ? null : response.status(), elapsedMs,
                inputTokens, outputTokens, reasoningTokens);

        if (text.isBlank()) {
            throw new AiServiceException("AI 응답이 비어 있습니다. 잠시 후 다시 시도해주세요.", null);
        }
        return new AiResult(text, model, inputTokens, outputTokens, reasoningTokens, elapsedMs);
    }

    /** output 목록 중 message 항목의 output_text만 이어 붙인다 (추론 요약 등은 건너뜀) */
    private String extractText(ResponsesApiResponse response) {
        if (response == null || response.output() == null) {
            return "";
        }
        return response.output().stream()
                .filter(item -> "message".equals(item.type()) && item.content() != null)
                .flatMap(item -> item.content().stream())
                .filter(part -> "output_text".equals(part.type()) && part.text() != null)
                .map(ContentPart::text)
                .collect(Collectors.joining());
    }

    // ===== Responses API 응답 중 필요한 부분만 받는 구조 =====

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResponsesApiResponse(String status, List<OutputItem> output, Usage usage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OutputItem(String type, List<ContentPart> content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContentPart(String type, String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(
            @JsonProperty("input_tokens") Integer inputTokens,
            @JsonProperty("output_tokens") Integer outputTokens,
            @JsonProperty("output_tokens_details") OutputTokensDetails outputTokensDetails
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OutputTokensDetails(@JsonProperty("reasoning_tokens") Integer reasoningTokens) {
    }
}