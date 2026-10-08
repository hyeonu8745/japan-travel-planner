package com.example.japantravelplanner.attraction.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * 관광지 상세 (Google Places에서 화면을 열 때마다 받아온다. 저장하지 않는다).
 *
 * 화면 표시 의무 (Google 약관):
 * - 이 내용을 보여주는 영역 안에 "Google Maps" 표시(로고 또는 글자)를 둔다 → attribution
 * - 사진을 크게 보여줄 때는 사진 작성자(authors)를, 리뷰에는 작성자 이름·사진을 함께 보여준다
 * - 원문을 볼 수 있게 googleMapsUri(장소·리뷰) 링크를 둔다
 * - 리뷰 정렬 기준과 "확인되지 않은 의견"이라는 안내(reviewNotice)를 보여준다. 번역된 리뷰는 표시하고 원문을 볼 수 있게 한다
 *
 * @param name          우리 DB의 장소 이름 (일정에 나오는 이름과 같다)
 * @param localizedName Google이 준 한국어 이름 (없으면 null)
 * @param summary       한 줄 소개 (Google 편집 요약. 없는 장소가 많다)
 * @param openingHours  요일별 영업시간 문장 (예: "월요일: 오전 9:00~오후 4:30"). 정보가 없으면 빈 배열
 * @param photos        사진 목록 (최대 5장). 사진 자체는 /photo 로 주소를 받아서 보여준다
 * @param reviews       리뷰 (Google이 고른 최대 5개)
 */
@JsonPropertyOrder({"attractionId", "placeId", "name", "localizedName", "summary", "rating", "userRatingCount",
        "openingHours", "websiteUri", "googleMapsUri", "photos", "reviews", "attribution", "reviewNotice"})
public record AttractionDetailResponseDto(
        Long attractionId,
        String placeId,
        String name,
        String localizedName,
        String summary,
        Double rating,
        Integer userRatingCount,
        List<String> openingHours,
        String websiteUri,
        String googleMapsUri,
        List<PhotoDto> photos,
        List<ReviewDto> reviews,
        String attribution,
        String reviewNotice
) {

    /**
     * @param name    사진 이름. GET /api/attractions/{id}/photo?name= 에 넣어서 사진 주소를 받는다 (저장 금지, 만료될 수 있음)
     * @param authors 사진 작성자. 있으면 사진 근처에 표시해야 한다 (빈 배열일 수 있음)
     */
    public record PhotoDto(String name, Integer widthPx, Integer heightPx, List<AuthorDto> authors) {
    }

    /** 작성자 이름, Google Maps 프로필 주소, 작성자 사진 주소 */
    public record AuthorDto(String name, String uri, String photoUri) {
    }

    /**
     * @param text             화면에 보여줄 글 (한국어로 번역되었을 수 있다)
     * @param translated       번역된 글인지. true면 "번역됨" 표시와 원문 보기를 둔다
     * @param originalText     원문 (번역되지 않았으면 null)
     * @param originalLanguage 원문 언어 코드 (예: "ja")
     * @param relativeTime     "3주 전" 같은 작성 시점
     * @param googleMapsUri    Google Maps에서 이 리뷰를 보는 주소
     */
    public record ReviewDto(AuthorDto author, Integer rating, String text, boolean translated, String originalText,
                            String originalLanguage, String relativeTime, String publishTime, String googleMapsUri) {
    }
}
