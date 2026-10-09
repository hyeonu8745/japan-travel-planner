package com.example.japantravelplanner.attraction.service;

import com.example.japantravelplanner.attraction.dto.AttractionDetailResponseDto;
import com.example.japantravelplanner.attraction.dto.PhotoUriResponseDto;
import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.attraction.repository.AttractionRepository;
import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.global.google.GooglePlacesClient;
import com.example.japantravelplanner.global.google.GooglePlacesClient.PlaceDetails;
import com.example.japantravelplanner.global.google.GoogleProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.example.japantravelplanner.attraction.service.PlaceContentMapper.text;

/**
 * 관광지 상세 정보 (사진·소개·영업시간·리뷰). Google Places에서 볼 때마다 받아온다.
 *
 * 약관상 place ID 말고는 저장·캐시할 수 없으므로 DB에도 메모리에도 남기지 않는다 (GooglePlacesClient 참고).
 * 호출할 때마다 요금이 들기 때문에 컨트롤러 경로는 로그인한 사용자만 쓸 수 있게 했다 (SecurityConfig).
 * DB 트랜잭션을 열지 않는다: 관광지 한 건만 읽고, 외부 호출 동안 DB 연결을 잡고 있지 않기 위해서다.
 * 사진·리뷰를 화면용으로 바꾸는 규칙은 식당 상세와 같이 쓴다 (PlaceContentMapper).
 */
@Service
@RequiredArgsConstructor
public class AttractionDetailService {

    static final int MAX_PHOTOS = PlaceContentMapper.MAX_PHOTOS;
    static final int MIN_PHOTO_WIDTH = PlaceContentMapper.MIN_PHOTO_WIDTH;
    static final int MAX_PHOTO_WIDTH = PlaceContentMapper.MAX_PHOTO_WIDTH;
    static final String ATTRIBUTION = PlaceContentMapper.ATTRIBUTION;
    static final String REVIEW_NOTICE = PlaceContentMapper.REVIEW_NOTICE;

    private final AttractionRepository attractionRepository;
    private final GooglePlacesClient placesClient;
    private final GoogleProperties googleProperties;

    public AttractionDetailResponseDto details(Long attractionId) {
        Attraction attraction = findWithPlaceId(attractionId);
        PlaceDetails place = placesClient.details(attraction.getPlaceId())
                .orElseThrow(() -> new NotFoundException("Google에서 이 장소의 정보를 찾지 못했어요."));

        return new AttractionDetailResponseDto(attraction.getId(), attraction.getPlaceId(), attraction.getName(),
                text(place.displayName()), text(place.editorialSummary()), place.rating(), place.userRatingCount(),
                PlaceContentMapper.openingHours(place), place.websiteUri(), place.googleMapsUri(),
                PlaceContentMapper.photos(place), PlaceContentMapper.reviews(place), ATTRIBUTION, REVIEW_NOTICE);
    }

    /**
     * 상세에서 받은 사진 이름으로 화면에 띄울 사진 주소를 받는다.
     * 이 관광지의 사진만 허용한다 (로그인한 사용자가 다른 장소 사진으로 요금을 쓰게 하지 않기 위해).
     */
    public PhotoUriResponseDto photo(Long attractionId, String photoName, Integer maxWidthPx) {
        Attraction attraction = findWithPlaceId(attractionId);
        PlaceContentMapper.requirePhotoOf(attraction.getPlaceId(), photoName);
        int width = PlaceContentMapper.photoWidth(maxWidthPx, googleProperties.getPhotoMaxWidthPx());

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
}
