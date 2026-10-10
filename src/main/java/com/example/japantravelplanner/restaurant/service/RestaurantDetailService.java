package com.example.japantravelplanner.restaurant.service;

import com.example.japantravelplanner.attraction.dto.PhotoUriResponseDto;
import com.example.japantravelplanner.attraction.service.PlaceContentMapper;
import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.global.google.GooglePlacesClient;
import com.example.japantravelplanner.global.google.GooglePlacesClient.PlaceDetails;
import com.example.japantravelplanner.global.google.GooglePlacesClient.RestaurantPlace;
import com.example.japantravelplanner.global.google.GoogleProperties;
import com.example.japantravelplanner.restaurant.dto.RestaurantDetailResponseDto;
import com.example.japantravelplanner.restaurant.dto.RestaurantDetailResponseDto.GoogleDto;
import com.example.japantravelplanner.restaurant.dto.RestaurantDetailResponseDto.GoogleStatus;
import com.example.japantravelplanner.restaurant.dto.RestaurantDetailResponseDto.TabelogDto;
import com.example.japantravelplanner.restaurant.entity.GoogleMatchStatus;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.restaurant.match.RestaurantMatchService;
import com.example.japantravelplanner.restaurant.review.ReviewInsightAnalyzer;
import com.example.japantravelplanner.attraction.dto.AttractionDetailResponseDto.ReviewDto;
import com.example.japantravelplanner.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

import static com.example.japantravelplanner.attraction.service.PlaceContentMapper.text;

/**
 * 식당 상세. 타베로그 정보는 항상 주고, Google 정보는 같은 가게를 찾았을 때만 볼 때마다 받아온다.
 *
 * - 아직 Google과 맞춰보지 않은 식당이면 여기서 먼저 맞춰본다 (Text Search 1건, 결과는 place ID만 저장)
 * - 찾지 못함·폐업·확인 실패여도 오류가 아니라 200 + 안내 문장 (타베로그 정보는 보여줄 수 있으므로)
 * - Google 상세 호출 자체가 실패하면 관광지 상세와 같이 503
 * - 트랜잭션을 열지 않는다 (외부 호출 동안 DB 연결을 잡지 않기 위해. 매칭 저장은 RestaurantMatchService가 따로 한다)
 */
@Service
@RequiredArgsConstructor
public class RestaurantDetailService {

    static final String NOT_FOUND_MESSAGE = "Google에서 이 식당을 찾지 못했어요. 폐업했거나 이름이 바뀌었을 수 있어요.";
    static final String CLOSED_MESSAGE = "Google에 폐업한 곳으로 표시된 식당이에요.";
    static final String UNCHECKED_MESSAGE = "지금은 Google 정보를 확인하지 못했어요. 잠시 후 다시 시도해주세요.";

    private final RestaurantRepository restaurantRepository;
    private final RestaurantMatchService matchService;
    private final GooglePlacesClient placesClient;
    private final GoogleProperties googleProperties;
    private final ReviewInsightAnalyzer reviewInsightAnalyzer;

    public RestaurantDetailResponseDto details(Long restaurantId) {
        Restaurant restaurant = find(restaurantId);
        if (restaurant.getGoogleMatchStatus() == null) {
            matchService.verify(Set.of(restaurantId));
            restaurant = find(restaurantId);
        }

        GoogleMatchStatus status = restaurant.getGoogleMatchStatus();
        if (status == null) return withoutGoogle(restaurant, GoogleStatus.UNCHECKED, UNCHECKED_MESSAGE);
        if (status == GoogleMatchStatus.NOT_FOUND) return withoutGoogle(restaurant, GoogleStatus.NOT_FOUND, NOT_FOUND_MESSAGE);
        if (status == GoogleMatchStatus.CLOSED) return withoutGoogle(restaurant, GoogleStatus.CLOSED, CLOSED_MESSAGE);

        RestaurantPlace restaurantPlace = placesClient.restaurantDetails(restaurant.getGooglePlaceId()).orElse(null);
        if (restaurantPlace == null) {
            // 저장해 둔 place ID가 Google에서 사라졌다 (폐업·통합 등). 지금은 안내만 하고 기록은 바꾸지 않는다
            return withoutGoogle(restaurant, GoogleStatus.NOT_FOUND, NOT_FOUND_MESSAGE);
        }

        PlaceDetails place = restaurantPlace.details();
        List<ReviewDto> reviews = PlaceContentMapper.reviews(place);
        GoogleDto google = new GoogleDto(restaurant.getGooglePlaceId(), text(place.displayName()),
                text(place.editorialSummary()), place.rating(), place.userRatingCount(),
                PlaceContentMapper.openingHours(place), place.websiteUri(), place.googleMapsUri(),
                PlaceContentMapper.photos(place), reviews,
                RestaurantFeatures.from(restaurantPlace),
                reviewInsightAnalyzer.analyze(reviews));   // 저장하지 않고 볼 때마다 분석, 실패하면 null
        return new RestaurantDetailResponseDto(restaurant.getId(), restaurant.getName(), restaurant.getGenre(),
                restaurant.getAddress(), tabelog(restaurant), GoogleStatus.MATCHED, null, google,
                PlaceContentMapper.ATTRIBUTION, PlaceContentMapper.REVIEW_NOTICE);
    }

    /** 상세에서 받은 사진 이름으로 사진 주소를 받는다. 이 식당(Google에서 찾은 곳)의 사진만 허용 */
    public PhotoUriResponseDto photo(Long restaurantId, String photoName, Integer maxWidthPx) {
        Restaurant restaurant = find(restaurantId);
        if (restaurant.getGoogleMatchStatus() != GoogleMatchStatus.MATCHED || restaurant.getGooglePlaceId() == null) {
            throw new NotFoundException("이 식당은 Google 사진이 없어요.");
        }
        PlaceContentMapper.requirePhotoOf(restaurant.getGooglePlaceId(), photoName);
        int width = PlaceContentMapper.photoWidth(maxWidthPx, googleProperties.getPhotoMaxWidthPx());

        String uri = placesClient.photoUri(photoName, width)
                .orElseThrow(() -> new NotFoundException("사진 정보가 만료됐어요. 식당 상세를 다시 불러와주세요."));
        return new PhotoUriResponseDto(uri);
    }

    private Restaurant find(Long restaurantId) {
        return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new NotFoundException("식당을 찾을 수 없습니다."));
    }

    private static RestaurantDetailResponseDto withoutGoogle(Restaurant r, GoogleStatus status, String message) {
        return new RestaurantDetailResponseDto(r.getId(), r.getName(), r.getGenre(), r.getAddress(), tabelog(r),
                status, message, null, null, null);
    }

    private static TabelogDto tabelog(Restaurant r) {
        return new TabelogDto(r.getRatingVal(), r.getReviewCnt(), r.getBudgetLunch(), r.getBudgetDinner(),
                r.getHoliday(), r.getNearestStation(), r.getNearestDistance());
    }
}
