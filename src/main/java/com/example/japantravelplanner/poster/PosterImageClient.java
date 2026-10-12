package com.example.japantravelplanner.poster;

import com.example.japantravelplanner.ai.config.OpenAiProperties;
import com.example.japantravelplanner.global.exception.AiServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** OpenAI 이미지 생성 (POST /images/generations). GPT 이미지 모델은 base64로만 돌려준다 */
@Slf4j
@Component
public class PosterImageClient {

    private final PosterProperties props;
    private final JsonMapper jsonMapper;
    private final RestClient restClient;

    public PosterImageClient(OpenAiProperties openAi, PosterProperties props, JsonMapper jsonMapper) {
        this.props = props;
        this.jsonMapper = jsonMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(props.getTimeoutSeconds()));
        this.restClient = RestClient.builder()
                .baseUrl(openAi.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openAi.getApiKey())
                .build();
    }

    /** @return PNG 바이트 */
    public byte[] generate(String prompt, String size) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getModel());
        body.put("prompt", prompt);
        body.put("size", size);
        body.put("quality", props.getQuality());
        body.put("output_format", "png");
        body.put("n", 1);
        long started = System.currentTimeMillis();
        try {
            String response = restClient.post().uri("/images/generations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve().body(String.class);
            byte[] image = parse(jsonMapper, response);
            log.info("[Poster] 이미지 생성 {} {} {} → {}KB, {}ms", props.getModel(), size, props.getQuality(),
                    image.length / 1024, System.currentTimeMillis() - started);
            return image;
        } catch (RestClientResponseException e) {
            log.warn("[Poster] 이미지 생성 실패 HTTP {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new AiServiceException("포스터 이미지를 만들지 못했어요. 잠시 후 다시 시도해주세요.", e);
        } catch (RestClientException e) {
            log.warn("[Poster] 이미지 생성 연결 실패: {}", e.getMessage());
            throw new AiServiceException("포스터 이미지를 만들지 못했어요. 잠시 후 다시 시도해주세요.", e);
        }
    }

    static byte[] parse(JsonMapper mapper, String response) {
        if (response == null || response.isBlank()) throw new AiServiceException("포스터 이미지 응답이 비어 있어요.", null);
        JsonNode data = mapper.readTree(response).path("data");
        String b64 = data.isArray() && !data.isEmpty() ? data.get(0).path("b64_json").asString("") : "";
        if (b64.isEmpty()) throw new AiServiceException("포스터 이미지 응답에 그림이 없어요.", null);
        return Base64.getDecoder().decode(b64);
    }
}
