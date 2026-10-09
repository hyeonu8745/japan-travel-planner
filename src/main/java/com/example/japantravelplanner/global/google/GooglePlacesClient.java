package com.example.japantravelplanner.global.google;

import com.example.japantravelplanner.global.exception.ExternalApiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Google Places API (New): 장소 상세(Place Details), 사진(Place Photos), 식당 매칭용 검색(Text Search).
 *
 * 약관상 place ID 말고는 Places 내용(사진·리뷰·영업시간·소개)을 저장하거나 캐시할 수 없다.
 * 그래서 화면에서 상세를 열 때마다 여기서 받아서 그대로 돌려주고, DB에도 메모리에도 남기지 않는다.
 * 사진 이름(photo name)도 저장할 수 없고 시간이 지나면 만료된다.
 *
 * 요금: 소개·리뷰를 받으므로 Place Details Enterprise + Atmosphere 등급(월 1,000건 무료),
 * 사진 주소는 Place Details Photos 등급(월 1,000건 무료)이 따로 든다.
 * 식당 매칭 검색은 이름·좌표·영업 상태를 받으므로 Text Search Pro 등급(월 5,000건 무료)이다.
 */
@Slf4j
@Component
public class GooglePlacesClient {

    /** 받을 필드 (필드에 따라 요금 등급이 정해진다) */
    static final String DETAILS_FIELD_MASK = String.join(",",
            "id", "displayName", "googleMapsUri", "editorialSummary", "regularOpeningHours",
            "rating", "userRatingCount", "websiteUri", "photos", "reviews");

    /**
     * 식당 상세에서 추가로 받는 특성 (아침·점심·저녁, 채식, 주류, 예약, 포장, 결제 수단 등).
     * 모두 Enterprise + Atmosphere 등급 안이라 상세 요금이 늘지 않는다 (10-09 문서 확인)
     */
    static final String RESTAURANT_FIELD_MASK = String.join(",", DETAILS_FIELD_MASK,
            "servesBreakfast", "servesBrunch", "servesLunch", "servesDinner", "servesVegetarianFood",
            "servesBeer", "servesWine", "servesCocktails", "servesCoffee", "servesDessert",
            "menuForChildren", "goodForChildren", "goodForGroups", "reservable",
            "dineIn", "takeout", "delivery", "outdoorSeating", "priceRange", "paymentOptions");

    /** 식당 매칭 검색에서 받을 필드. 이름·좌표·영업 상태 → Text Search Pro 등급 (place ID만 받는 무료 등급으로는 같은 가게인지 확인할 수 없다) */
    static final String SEARCH_FIELD_MASK = String.join(",",
            "places.id", "places.displayName", "places.location", "places.businessStatus");

    /** 매칭은 타베로그 일본어 이름과 비교하므로 일본어 이름으로 받는다 (상세 화면용 places-language와 별개) */
    static final String SEARCH_LANGUAGE = "ja";

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
        Optional<PlaceDetails> details = fetchDetails(placeId, DETAILS_FIELD_MASK, PlaceDetails.class);
        details.ifPresent(d -> log.info("[Places] 상세 placeId={} 사진 {}장 리뷰 {}개", placeId,
                d.photos() == null ? 0 : d.photos().size(), d.reviews() == null ? 0 : d.reviews().size()));
        return details;
    }

    /** 식당 상세 (관광지와 같은 내용 + 식당 특성). Google에 없는 장소면 빈 값 */
    public Optional<RestaurantPlace> restaurantDetails(String placeId) {
        Optional<RestaurantPlace> details = fetchDetails(placeId, RESTAURANT_FIELD_MASK, RestaurantPlace.class);
        details.ifPresent(d -> log.info("[Places] 식당 상세 placeId={} 사진 {}장 리뷰 {}개", placeId,
                d.photos() == null ? 0 : d.photos().size(), d.reviews() == null ? 0 : d.reviews().size()));
        return details;
    }

    private <T> Optional<T> fetchDetails(String placeId, String fieldMask, Class<T> type) {
        try {
            T details = restClient.get()
                    .uri(builder -> builder.path("/places/{placeId}")
                            .queryParam("languageCode", properties.getPlacesLanguage())
                            .build(placeId))
                    .header("X-Goog-Api-Key", properties.getApiKey())
                    .header("X-Goog-FieldMask", fieldMask)
                    .retrieve()
                    .body(type);
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

    /**
     * 글자로 장소 검색 (식당 매칭용). 받은 이름·좌표는 같은 가게인지 비교하는 데만 쓰고 저장하지 않는다.
     *
     * @param query        "식당 이름 + 주소"
     * @param radiusMeters 이 원 안을 우선해달라는 뜻 (밖의 결과도 올 수 있다)
     */
    public List<SearchPlace> searchText(String query, double lat, double lng, double radiusMeters, int pageSize) {
        Map<String, Object> body = Map.of(
                "textQuery", query,
                "languageCode", SEARCH_LANGUAGE,
                "regionCode", "JP",
                "pageSize", pageSize,
                "locationBias", Map.of("circle", Map.of(
                        "center", Map.of("latitude", lat, "longitude", lng),
                        "radius", radiusMeters)));
        try {
            SearchResponse response = restClient.post()
                    .uri("/places:searchText")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Goog-Api-Key", properties.getApiKey())
                    .header("X-Goog-FieldMask", SEARCH_FIELD_MASK)
                    .body(body)
                    .retrieve()
                    .body(SearchResponse.class);
            return response == null || response.places() == null ? List.of() : response.places();
        } catch (RestClientResponseException e) {
            log.error("[Places] 검색 실패 status={} body={}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new ExternalApiException("장소를 검색하지 못했어요. 잠시 후 다시 시도해주세요.", e);
        } catch (RestClientException e) {
            log.error("[Places] 검색 연결 실패: {}", e.getClass().getSimpleName());
            throw new ExternalApiException("장소를 검색하지 못했어요. 잠시 후 다시 시도해주세요.", e);
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

    /** 식당 상세: PlaceDetails와 같은 내용 + 식당 특성 (값이 없으면 null = 정보 없음) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RestaurantPlace(String id, LocalizedText displayName, String googleMapsUri,
                                  LocalizedText editorialSummary, OpeningHours regularOpeningHours,
                                  Double rating, Integer userRatingCount, String websiteUri,
                                  List<Photo> photos, List<Review> reviews,
                                  Boolean servesBreakfast, Boolean servesBrunch, Boolean servesLunch,
                                  Boolean servesDinner, Boolean servesVegetarianFood,
                                  Boolean servesBeer, Boolean servesWine, Boolean servesCocktails,
                                  Boolean servesCoffee, Boolean servesDessert,
                                  Boolean menuForChildren, Boolean goodForChildren, Boolean goodForGroups,
                                  Boolean reservable, Boolean dineIn, Boolean takeout, Boolean delivery,
                                  Boolean outdoorSeating, PriceRange priceRange, PaymentOptions paymentOptions) {

        /** 관광지와 같은 공통 내용 (사진·리뷰·영업시간 변환을 같이 쓰기 위해) */
        public PlaceDetails details() {
            return new PlaceDetails(id, displayName, googleMapsUri, editorialSummary, regularOpeningHours,
                    rating, userRatingCount, websiteUri, photos, reviews);
        }
    }

    /** 1인 가격대. endPrice가 없으면 "startPrice 이상" */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PriceRange(Money startPrice, Money endPrice) {
    }

    /** units는 JSON에서 문자열(int64)로 온다 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Money(String currencyCode, String units, Integer nanos) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentOptions(Boolean acceptsCreditCards, Boolean acceptsDebitCards,
                                 Boolean acceptsCashOnly, Boolean acceptsNfc) {
    }

    /** 검색 결과 한 곳. businessStatus: OPERATIONAL, CLOSED_TEMPORARILY, CLOSED_PERMANENTLY */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchPlace(String id, LocalizedText displayName, LatLng location, String businessStatus) {

        public String nameText() {
            return displayName == null ? null : displayName.text();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LatLng(Double latitude, Double longitude) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchResponse(List<SearchPlace> places) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PhotoMedia(String name, String photoUri) {
    }
}
