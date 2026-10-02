package com.example.japantravelplanner.global.google;

import com.example.japantravelplanner.global.exception.ExternalApiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Google Geocoding API: 주소 → 좌표
 * 결과가 없으면 빈 값, 키 오류·장애는 ExternalApiException
 */
@Slf4j
@Component
public class GoogleGeocodingClient {

    private final GoogleProperties properties;
    private final RestClient restClient;

    public GoogleGeocodingClient(GoogleProperties properties) {
        this.properties = properties;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));

        this.restClient = RestClient.builder()
                .baseUrl("https://maps.googleapis.com/maps/api/geocode/json")
                .requestFactory(requestFactory)
                .build();
    }

    public record GeocodeResult(double lat, double lng, String formattedAddress) {
    }

    public Optional<GeocodeResult> geocode(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }

        GeocodeResponse response;
        try {
            response = restClient.get()
                    .uri(builder -> builder
                            .queryParam("address", address)
                            .queryParam("components", "country:JP")   // 일본 안에서만 찾기
                            .queryParam("language", "ja")
                            .queryParam("key", properties.getApiKey())
                            .build())
                    .retrieve()
                    .body(GeocodeResponse.class);
        } catch (RestClientException e) {
            // 주의: 요청 주소(URL)에 키가 들어 있으므로 URL을 로그에 남기지 않는다
            log.error("[Geocoding] 연결 실패: {}", e.getClass().getSimpleName());
            throw new ExternalApiException("주소를 확인하지 못했습니다. 잠시 후 다시 시도해주세요.", e);
        }

        String status = response == null ? "NO_RESPONSE" : response.status();
        switch (status) {
            case "OK" -> {
                Result first = response.results().get(0);
                log.info("[Geocoding] OK → {}", first.formattedAddress());
                return Optional.of(new GeocodeResult(
                        first.geometry().location().lat(),
                        first.geometry().location().lng(),
                        first.formattedAddress()));
            }
            case "ZERO_RESULTS" -> {
                log.info("[Geocoding] 결과 없음");
                return Optional.empty();
            }
            default -> {
                // REQUEST_DENIED(키·권한 문제), OVER_QUERY_LIMIT 등
                log.error("[Geocoding] 실패 status={} message={}",
                        status, response == null ? null : response.errorMessage());
                throw new ExternalApiException("주소를 확인하지 못했습니다. 잠시 후 다시 시도해주세요.", null);
            }
        }
    }

    // ===== 응답 중 필요한 부분 =====

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeocodeResponse(String status, List<Result> results,
                           @JsonProperty("error_message") String errorMessage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result(Geometry geometry, @JsonProperty("formatted_address") String formattedAddress) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Geometry(Location location) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Location(double lat, double lng) {
    }
}