package com.example.japantravelplanner.attraction.service;

import com.example.japantravelplanner.attraction.dto.AttractionDetailResponseDto.AuthorDto;
import com.example.japantravelplanner.attraction.dto.AttractionDetailResponseDto.PhotoDto;
import com.example.japantravelplanner.attraction.dto.AttractionDetailResponseDto.ReviewDto;
import com.example.japantravelplanner.global.google.GooglePlacesClient.AuthorAttribution;
import com.example.japantravelplanner.global.google.GooglePlacesClient.LocalizedText;
import com.example.japantravelplanner.global.google.GooglePlacesClient.PlaceDetails;
import com.example.japantravelplanner.global.google.GooglePlacesClient.Review;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Google 장소 상세(사진·리뷰·영업시간)를 화면용 값으로 바꾸는 공통 규칙. 관광지 상세와 식당 상세가 같이 쓴다.
 * (사진·리뷰 DTO는 관광지 상세에서 먼저 만든 것을 그대로 쓴다. 응답 JSON 모양을 두 화면이 똑같이 쓰기 위해서)
 */
public final class PlaceContentMapper {

    /** 상세에 담는 사진 수 (사진은 볼 때마다 따로 요금이 들어서 많이 주지 않는다) */
    public static final int MAX_PHOTOS = 5;

    public static final int MIN_PHOTO_WIDTH = 100;
    public static final int MAX_PHOTO_WIDTH = 1600;

    public static final String ATTRIBUTION = "Google Maps";
    public static final String REVIEW_NOTICE = "Google 리뷰 중 관련성이 높은 최대 5개를 보여드려요. 작성자를 확인하지 않은 사용자 의견이에요.";

    /** Google 사진 이름 형식: places/{placeId}/photos/{photoId} */
    private static final Pattern PHOTO_NAME = Pattern.compile("places/[A-Za-z0-9_-]+/photos/[A-Za-z0-9_-]+");

    private PlaceContentMapper() {
    }

    public static List<PhotoDto> photos(PlaceDetails place) {
        return place.photos() == null ? List.of() : place.photos().stream()
                .filter(p -> p.name() != null)
                .limit(MAX_PHOTOS)
                .map(p -> new PhotoDto(p.name(), p.widthPx(), p.heightPx(), authors(p.authorAttributions())))
                .toList();
    }

    public static List<ReviewDto> reviews(PlaceDetails place) {
        return place.reviews() == null ? List.of()
                : place.reviews().stream().map(PlaceContentMapper::review).toList();
    }

    public static List<String> openingHours(PlaceDetails place) {
        return place.regularOpeningHours() == null || place.regularOpeningHours().weekdayDescriptions() == null
                ? List.of() : place.regularOpeningHours().weekdayDescriptions();
    }

    /**
     * 사진 요청이 그 장소의 사진인지 확인한다 (로그인한 사용자가 다른 장소 사진으로 요금을 쓰게 하지 않기 위해).
     * 아니면 400
     */
    public static void requirePhotoOf(String placeId, String photoName) {
        if (photoName == null || !PHOTO_NAME.matcher(photoName).matches()
                || !photoName.startsWith("places/" + placeId + "/photos/")) {
            throw new IllegalArgumentException("이 장소의 사진이 아니에요.");
        }
    }

    /** 요청한 가로 크기. 없거나 범위(100~1600)를 벗어나면 기본값 */
    public static int photoWidth(Integer requested, int defaultWidth) {
        return requested == null || requested < MIN_PHOTO_WIDTH || requested > MAX_PHOTO_WIDTH
                ? defaultWidth : requested;
    }

    /** 번역된 리뷰면 원문도 함께 준다 (번역 표시와 원문 보기는 약관상 필요하다) */
    static ReviewDto review(Review r) {
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
                : attributions.stream().map(PlaceContentMapper::author).toList();
    }

    private static AuthorDto author(AuthorAttribution a) {
        return a == null ? null : new AuthorDto(a.displayName(), a.uri(), a.photoUri());
    }

    public static String text(LocalizedText t) {
        return t == null || t.text() == null || t.text().isBlank() ? null : t.text();
    }
}
