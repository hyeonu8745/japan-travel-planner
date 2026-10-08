package com.example.japantravelplanner.global.google;

import com.example.japantravelplanner.global.exception.ExternalApiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Google Places API (New): 장소 상세(Place Details)와 사진(Place Photos).
 *
 * 약관상 place ID 말고는 Places 내용(사진·리뷰·영업시간·소개)을 저장하거나 캐시할 수 없다.
 * 그래서 화면에서 상세를 열 때마다 여기서 받아서 그대로 돌려주고, DB에도 메모리에도 남기지 않는다.
 * 사진 이름(photo name)도 저장할 수 없고 시간이 지나면 만료된다.
 *
 * 요금: 소개·리뷰를 받으므로 Place Details Enterprise + Atmosphere 등급(월 1,000건 무료),
 * 사진 주소는 Place Details Photos 등급(월 1,000건 무료)이 따로 든다.
 */
@Slf4j
@Component
public class GooglePlacesClient {

    /** 받을 필드 (필드에 따라 요금 등급이 정해진다) */
    static final String DETAILS_FIELD_MASK = String.join(",",
            "id", "displayName", "googleMapsUri", "editorialSummary", "regularOpeningHours",
            "rating", "userRatingCount", "websiteUri", "photos", "reviews");

    private final GoogleProperties properties;
    private final RestClient restClient;

    public GooglePlacesClient(GoogleProperties properties) {
        this.properties = properties;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));

        this.restClient = RestClient.builder()
                .baseUrl("https://places.googleapis.com/v1")
                .requestFactory(requestFactory)
                .build();
    }

    /** 장소 상세. Google에 없는 장소(폐업·삭제)면 빈 값 */
    public Optional<PlaceDetails> details(String placeId) {
        try {
            PlaceDetails details = restClient.get()
                    .uri(builder -> builder.path("/places/{placeId}")
                            .queryParam("languageCode", properties.getPlacesLanguage())
                            .build(placeId))
                    .header("X-Goog-Api-Key", properties.getApiKey())
                    .header("X-Goog-FieldMask", DETAILS_FIELD_MASK)
                    .retrieve()
                    .body(PlaceDetails.class);
            log.info("[Places] 상세 placeId={} 사진 {}장 리뷰 {}개", placeId,
                    details == null || details.photos() == null ? 0 : details.photos().size(),
                    details == null || details.reviews() == null ? 0 : details.reviews().size());
            return Optional.ofNullable(details);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                log.warn("[Places] 장소 없음 placeId={}", placeId);
                return Optional.empty();
            }
            log.error("[Places] 상세 실패 status={} body={}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new ExternalApiException("장소 정보를 불러오지 못했어요. 잠시 후 다시 시도해주세요.", e);
        } catch (RestClientException e) {
            log.error("[Places] 연결 실패: {}", e.getClass().getSimpleName());
            throw new ExternalApiException("장소 정보를 불러오지 못했어요. 잠시 후 다시 시도해주세요.", e);
        }
    }

    /**
     * 사진 이름 → 화면에서 바로 쓸 수 있는 사진 주소 (API 키가 들어 있지 않은 주소).
     * 이름이 만료됐거나 잘못됐으면 빈 값 (상세를 다시 불러서 새 이름을 받아야 한다)
     */
    public Optional<String> photoUri(String photoName, int maxWidthPx) {
        try {
            PhotoMedia media = restClient.get()
                    // 사진 이름에는 '/'가 들어 있어서(places/…/photos/…) 템플릿 변수로 넣으면 %2F로 바뀐다.
                    // 그래서 경로에 그대로 붙인다. 이름 형식은 호출하는 쪽(AttractionDetailService)이 검사한다
                    .uri(builder -> builder.path("/" + photoName + "/media")
                            .queryParam("maxWidthPx", maxWidthPx)
                            .queryParam("skipHttpRedirect", true)
                            .build())
                    .header("X-Goog-Api-Key", properties.getApiKey())
                    .retrieve()
                    .body(PhotoMedia.class);
            return Optional.ofNullable(media).map(PhotoMedia::photoUri);
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            if (status.value() == 400 || status.value() == 404) {
                log.warn("[Places] 사진 이름 만료·잘못됨 status={}", status.value());
                return Optional.empty();
            }
            log.error("[Places] 사진 실패 status={} body={}", status.value(), e.getResponseBodyAsString());
            throw new ExternalApiException("사진을 불러오지 못했어요. 잠시 후 다시 시도해주세요.", e);
        } catch (RestClientException e) {
            log.error("[Places] 사진 연결 실패: {}", e.getClass().getSimpleName());
            throw new ExternalApiException("사진을 불러오지 못했어요. 잠시 후 다시 시도해주세요.", e);
        }
    }

    // ===== 응답 중 필요한 부분 =====

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlaceDetails(String id, LocalizedText displayName, String googleMapsUri,
                               LocalizedText editorialSummary, OpeningHours regularOpeningHours,
                               Double rating, Integer userRatingCount, String websiteUri,
                               List<Photo> photos, List<Review> reviews) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LocalizedText(String text, String languageCode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OpeningHours(List<String> weekdayDescriptions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Photo(String name, Integer widthPx, Integer heightPx, List<AuthorAttribution> authorAttributions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthorAttribution(String displayName, String uri, String photoUri) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Review(String relativePublishTimeDescription, Integer rating, LocalizedText text,
                         LocalizedText originalText, AuthorAttribution authorAttribution, String publishTime,
                         String googleMapsUri) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PhotoMedia(String name, String photoUri) {
    }
}
