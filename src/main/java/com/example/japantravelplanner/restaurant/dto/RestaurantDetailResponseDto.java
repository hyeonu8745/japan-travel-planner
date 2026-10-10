package com.example.japantravelplanner.restaurant.dto;

import com.example.japantravelplanner.attraction.dto.AttractionDetailResponseDto.PhotoDto;
import com.example.japantravelplanner.attraction.dto.AttractionDetailResponseDto.ReviewDto;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * 식당 상세: 타베로그 정보(우리 DB)와 Google 정보(볼 때마다 받아옴, 저장하지 않음)를 나란히.
 * 두 평점을 함께 보여주고 사용자가 비교한다 (교차 검증 A안, 10-09 결정).
 *
 * Google 쪽 화면 표시 의무는 관광지 상세와 같다 (attribution "Google Maps", 사진·리뷰 작성자, 번역 표시·원문, 원문 링크).
 *
 * @param googleStatus  Google 정보 상태. MATCHED일 때만 google이 있다
 * @param googleMessage google이 없을 때 화면에 보여줄 안내 (MATCHED면 null)
 * @param google        Google 정보 (없으면 null)
 * @param attribution   google이 있을 때 "Google Maps" (없으면 null)
 * @param reviewNotice  google이 있을 때 리뷰 안내 문장 (없으면 null)
 */
@JsonPropertyOrder({"restaurantId", "name", "genre", "address", "tabelog", "googleStatus", "googleMessage",
        "google", "attribution", "reviewNotice"})
public record RestaurantDetailResponseDto(
        Long restaurantId,
        String name,
        String genre,
        String address,
        TabelogDto tabelog,
        GoogleStatus googleStatus,
        String googleMessage,
        GoogleDto google,
        String attribution,
        String reviewNotice
) {

    /**
     * MATCHED: Google에서 같은 가게를 찾음 / NOT_FOUND: 찾지 못함(폐업·이름 변경 가능성) /
     * CLOSED: Google에 폐업으로 표시됨 / UNCHECKED: 지금은 확인하지 못함 (Google 장애 등)
     */
    public enum GoogleStatus {
        MATCHED, NOT_FOUND, CLOSED, UNCHECKED
    }

    /** 타베로그 정보 (캐글 데이터라 몇 년 전 값일 수 있다) */
    public record TabelogDto(Double rating, Integer reviewCount, String budgetLunch, String budgetDinner,
                             String holiday, String nearestStation, Integer nearestDistance) {
    }

    /**
     * Google 정보
     * @param localizedName 한국어 이름 (없으면 null)
     * @param summary       한 줄 소개 (식당은 없는 곳이 많다)
     * @param photos        최대 5장. GET /api/restaurants/{id}/photo?name= 로 주소를 받는다
     * @param features      식당 특성 (10-09 추가)
     * @param reviewInsights 리뷰 분석: 차별·불친절·위생·한국인에게 좋은 점 (10-10 추가). 리뷰가 없거나 분석 실패면 null
     */
    public record GoogleDto(String placeId, String localizedName, String summary, Double rating,
                            Integer userRatingCount, List<String> openingHours, String websiteUri,
                            String googleMapsUri, List<PhotoDto> photos, List<ReviewDto> reviews,
                            FeaturesDto features, ReviewInsightsDto reviewInsights) {
    }

    /**
     * Google이 알려준 식당 특성. 각 값은 true(있음) / false(없음) / null(정보 없음).
     * 화면에서는 labels를 그대로 아이콘·칩으로 쓰면 된다 (true인 것만, 정해진 순서).
     *
     * @param priceMinYen 1인 가격대 하한 (엔, Google 기준. 엔화가 아니거나 정보가 없으면 null)
     * @param priceMaxYen 1인 가격대 상한 (없으면 "하한 이상")
     * @param labels      true인 특성의 한국어 이름 (예: "점심", "채식 메뉴", "예약 가능", "카드 결제")
     */
    public record FeaturesDto(Boolean servesBreakfast, Boolean servesBrunch, Boolean servesLunch, Boolean servesDinner,
                              Boolean servesVegetarianFood, Boolean servesBeer, Boolean servesWine,
                              Boolean servesCocktails, Boolean servesCoffee, Boolean servesDessert,
                              Boolean menuForChildren, Boolean goodForChildren, Boolean goodForGroups,
                              Boolean reservable, Boolean dineIn, Boolean takeout, Boolean delivery,
                              Boolean outdoorSeating, Boolean acceptsCreditCards, Boolean acceptsCashOnly,
                              Boolean acceptsNfc, Integer priceMinYen, Integer priceMaxYen, List<String> labels) {
    }
}
