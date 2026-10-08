package com.example.japantravelplanner.attraction.service;

import com.example.japantravelplanner.attraction.dto.AttractionDetailResponseDto;
import com.example.japantravelplanner.attraction.dto.AttractionDetailResponseDto.AuthorDto;
import com.example.japantravelplanner.attraction.dto.AttractionDetailResponseDto.PhotoDto;
import com.example.japantravelplanner.attraction.dto.AttractionDetailResponseDto.ReviewDto;
import com.example.japantravelplanner.attraction.dto.PhotoUriResponseDto;
import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.attraction.repository.AttractionRepository;
import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.global.google.GooglePlacesClient;
import com.example.japantravelplanner.global.google.GooglePlacesClient.AuthorAttribution;
import com.example.japantravelplanner.global.google.GooglePlacesClient.LocalizedText;
import com.example.japantravelplanner.global.google.GooglePlacesClient.PlaceDetails;
import com.example.japantravelplanner.global.google.GooglePlacesClient.Review;
import com.example.japantravelplanner.global.google.GoogleProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 관광지 상세 정보 (사진·소개·영업시간·리뷰). Google Places에서 볼 때마다 받아온다.
 *
 * 약관상 place ID 말고는 저장·캐시할 수 없으므로 DB에도 메모리에도 남기지 않는다 (GooglePlacesClient 참고).
 * 호출할 때마다 요금이 들기 때문에 컨트롤러 경로는 로그인한 사용자만 쓸 수 있게 했다 (SecurityConfig).
 * DB 트랜잭션을 열지 않는다: 관광지 한 건만 읽고, 외부 호출 동안 DB 연결을 잡고 있지 않기 위해서다.
 */
@Service
@RequiredArgsConstructor
public class AttractionDetailService {

    /** 상세에 담는 사진 수 (사진은 볼 때마다 따로 요금이 들어서 많이 주지 않는다) */
    static final int MAX_PHOTOS = 5;

    static final int MIN_PHOTO_WIDTH = 100;
    static final int MAX_PHOTO_WIDTH = 1600;

    static final String ATTRIBUTION = "Google Maps";
    static final String REVIEW_NOTICE = "Google 리뷰 중 관련성이 높은 최대 5개를 보여드려요. 작성자를 확인하지 않은 사용자 의견이에요.";

    /** Google 사진 이름 형식: places/{placeId}/photos/{photoId} */
    private static final Pattern PHOTO_NAME = Pattern.compile("places/[A-Za-z0-9_-]+/photos/[A-Za-z0-9_-]+");

    private final AttractionRepository attractionRepository;
    private final GooglePlacesClient placesClient;
    private final GoogleProperties googleProperties;

    public AttractionDetailResponseDto details(Long attractionId) {
        Attraction attraction = findWithPlaceId(attractionId);
        PlaceDetails place = placesClient.details(attraction.getPlaceId())
                .orElseThrow(() -> new NotFoundException("Google에서 이 장소의 정보를 찾지 못했어요."));

        List<PhotoDto> photos = place.photos() == null ? List.of() : place.photos().stream()
                .filter(p -> p.name() != null)
                .limit(MAX_PHOTOS)
                .map(p -> new PhotoDto(p.name(), p.widthPx(), p.heightPx(), authors(p.authorAttributions())))
                .toList();
        List<ReviewDto> reviews = place.reviews() == null ? List.of()
                : place.reviews().stream().map(AttractionDetailService::review).toList();
        List<String> openingHours = place.regularOpeningHours() == null
                || place.regularOpeningHours().weekdayDescriptions() == null
                ? List.of() : place.regularOpeningHours().weekdayDescriptions();

        return new AttractionDetailResponseDto(attraction.getId(), attraction.getPlaceId(), attraction.getName(),
                text(place.displayName()), text(place.editorialSummary()), place.rating(), place.userRatingCount(),
                openingHours, place.websiteUri(), place.googleMapsUri(), photos, reviews, ATTRIBUTION, REVIEW_NOTICE);
    }

    /**
     * 상세에서 받은 사진 이름으로 화면에 띄울 사진 주소를 받는다.
     * 이 관광지의 사진만 허용한다 (로그인한 사용자가 다른 장소 사진으로 요금을 쓰게 하지 않기 위해).
     */
    public PhotoUriResponseDto photo(Long attractionId, String photoName, Integer maxWidthPx) {
        Attraction attraction = findWithPlaceId(attractionId);
        if (photoName == null || !PHOTO_NAME.matcher(photoName).matches()
                || !photoName.startsWith("places/" + attraction.getPlaceId() + "/photos/")) {
            throw new IllegalArgumentException("이 장소의 사진이 아니에요.");
        }
        int width = maxWidthPx == null || maxWidthPx < MIN_PHOTO_WIDTH || maxWidthPx > MAX_PHOTO_WIDTH
                ? googleProperties.getPhotoMaxWidthPx() : maxWidthPx;

        String uri = placesClient.photoUri(photoName, width)
                .orElseThrow(() -> new NotFoundException("사진 정보가 만료됐어요. 장소 상세를 다시 불러와주세요."));
        return new PhotoUriResponseDto(uri);
    }

    private Attraction findWithPlaceId(Long attractionId) {
        Attraction attraction = attractionRepository.findById(attractionId)
                .orElseThrow(() -> new NotFoundException("관광지를 찾을 수 없습니다."));
        if (attraction.getPlaceId() == null || attraction.getPlaceId().isBlank()) {
            throw new NotFoundException("이 장소는 상세 정보가 없어요.");
        }
        return attraction;
    }

    /** 번역된 리뷰면 원문도 함께 준다 (번역 표시와 원문 보기는 약관상 필요하다) */
    private static ReviewDto review(Review r) {
        String shown = text(r.text());
        String original = text(r.originalText());
        String originalLanguage = r.originalText() == null ? null : r.originalText().languageCode();
        String shownLanguage = r.text() == null ? null : r.text().languageCode();
        boolean translated = shown != null && original != null && !Objects.equals(shownLanguage, originalLanguage);
        return new ReviewDto(author(r.authorAttribution()), r.rating(), shown != null ? shown : original,
                translated, translated ? original : null, originalLanguage,
                r.relativePublishTimeDescription(), r.publishTime(), r.googleMapsUri());
    }

    private static List<AuthorDto> authors(List<AuthorAttribution> attributions) {
        return attributions == null ? List.of()
                : attributions.stream().map(AttractionDetailService::author).toList();
    }

    private static AuthorDto author(AuthorAttribution a) {
        return a == null ? null : new AuthorDto(a.displayName(), a.uri(), a.photoUri());
    }

    private static String text(LocalizedText t) {
        return t == null || t.text() == null || t.text().isBlank() ? null : t.text();
    }
}
