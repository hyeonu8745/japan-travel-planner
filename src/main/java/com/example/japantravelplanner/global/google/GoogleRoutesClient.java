package com.example.japantravelplanner.global.google;

import com.example.japantravelplanner.global.exception.ExternalApiException;
import com.example.japantravelplanner.itinerary.travel.LegMode;
import com.example.japantravelplanner.itinerary.travel.TravelPoint;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Google Routes API computeRouteMatrix: 여러 출발지 × 여러 도착지의 이동 시간을 한 번에
 */
@Slf4j
@Component
public class GoogleRoutesClient {

    private final GoogleProperties properties;
    private final RestClient restClient;

    public GoogleRoutesClient(GoogleProperties properties) {
        this.properties = properties;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));

        this.restClient = RestClient.builder()
                .baseUrl("https://routes.googleapis.com")
                .requestFactory(requestFactory)
                .build();
    }

    /** API가 준 한 구간. 경로가 없으면 결과에 들어가지 않음 */
    public record RouteElement(int originIndex, int destinationIndex, int seconds, int distanceMeters) {
    }

    /** 경로가 있는 구간만 돌려준다. 일본은 대중교통(TRANSIT)을 제공하지 않으므로 WALK·DRIVE만 쓴다 */
    public List<RouteElement> computeMatrix(List<TravelPoint> origins, List<TravelPoint> destinations, LegMode mode) {
        if (mode == LegMode.TRANSIT) {
            throw new IllegalArgumentException("Routes API는 일본 대중교통 경로를 제공하지 않습니다.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("origins", origins.stream().map(this::waypoint).toList());
        body.put("destinations", destinations.stream().map(this::waypoint).toList());
        body.put("travelMode", mode == LegMode.DRIVE ? "DRIVE" : "WALK");
        if (mode == LegMode.DRIVE) {
            body.put("routingPreference", "TRAFFIC_UNAWARE");
        }

        MatrixElement[] response;
        try {
            response = restClient.post()
                    .uri("/distanceMatrix/v2:computeRouteMatrix")
                    .header("X-Goog-Api-Key", properties.getApiKey())
                    // 필요한 필드만 요청 (응답 크기와 요금 등급을 줄임)
                    .header("X-Goog-FieldMask", "originIndex,destinationIndex,duration,distanceMeters,condition")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(MatrixElement[].class);
        } catch (RestClientResponseException e) {
            log.error("[Routes] 호출 실패 status={} body={}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new ExternalApiException("이동 시간을 확인하지 못했습니다.", e);
        } catch (RestClientException e) {
            log.error("[Routes] 연결 실패: {}", e.getClass().getSimpleName());
            throw new ExternalApiException("이동 시간을 확인하지 못했습니다.", e);
        }

        List<RouteElement> result = new ArrayList<>();
        if (response == null) return result;
        for (MatrixElement e : response) {
            if (!"ROUTE_EXISTS".equals(e.condition()) || e.duration() == null) continue;
            result.add(new RouteElement(
                    // 값이 0이면 응답에서 생략되므로 null은 0으로
                    e.originIndex() == null ? 0 : e.originIndex(),
                    e.destinationIndex() == null ? 0 : e.destinationIndex(),
                    parseSeconds(e.duration()),
                    e.distanceMeters() == null ? 0 : e.distanceMeters()));
        }
        log.info("[Routes] mode={} 요청 {}x{} → 경로 {}개", mode, origins.size(), destinations.size(), result.size());
        return result;
    }

    private Map<String, Object> waypoint(TravelPoint p) {
        return Map.of("waypoint", Map.of("location", Map.of("latLng",
                Map.of("latitude", p.lat(), "longitude", p.lng()))));
    }

    /** "754s" → 754 */
    private int parseSeconds(String duration) {
        return Integer.parseInt(duration.replace("s", ""));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MatrixElement(Integer originIndex, Integer destinationIndex, String duration,
                         Integer distanceMeters, String condition) {
    }
}