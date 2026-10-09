package com.example.japantravelplanner.restaurant.service;

import com.example.japantravelplanner.global.exception.ExternalApiException;
import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.global.google.GooglePlacesClient;
import com.example.japantravelplanner.global.google.GooglePlacesClient.AuthorAttribution;
import com.example.japantravelplanner.global.google.GooglePlacesClient.LocalizedText;
import com.example.japantravelplanner.global.google.GooglePlacesClient.OpeningHours;
import com.example.japantravelplanner.global.google.GooglePlacesClient.Photo;
import com.example.japantravelplanner.global.google.GooglePlacesClient.Money;
import com.example.japantravelplanner.global.google.GooglePlacesClient.PaymentOptions;
import com.example.japantravelplanner.global.google.GooglePlacesClient.PriceRange;
import com.example.japantravelplanner.global.google.GooglePlacesClient.RestaurantPlace;
import com.example.japantravelplanner.global.google.GooglePlacesClient.Review;
import com.example.japantravelplanner.global.google.GoogleProperties;
import com.example.japantravelplanner.restaurant.dto.RestaurantDetailResponseDto;
import com.example.japantravelplanner.restaurant.dto.RestaurantDetailResponseDto.GoogleStatus;
import com.example.japantravelplanner.restaurant.entity.GoogleMatchStatus;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.restaurant.match.RestaurantMatchService;
import com.example.japantravelplanner.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 식당 상세: 타베로그 정보는 항상, Google 정보는 같은 가게를 찾았을 때만 (Google·매칭은 가짜) */
class RestaurantDetailServiceTest {

    private static final String PLACE_ID = "ChIJ_sushi_123";
    private static final String PHOTO = "places/" + PLACE_ID + "/photos/AelY_abc-1";

    private final RestaurantRepository repository = mock(RestaurantRepository.class);
    private final RestaurantMatchService matchService = mock(RestaurantMatchService.class);
    private final GooglePlacesClient placesClient = mock(GooglePlacesClient.class);
    private final GoogleProperties properties = new GoogleProperties();
    private RestaurantDetailService service;

    private final Restaurant sushi = Restaurant.builder().id(1L).name("鮨いしかわ").genre("寿司")
            .address("東京都新宿区").ratingVal(3.85).reviewCnt(420).budgetLunch("￥3,000～￥3,999")
            .budgetDinner("￥15,000～￥19,999").holiday("日曜日").nearestStation("新宿三丁目駅").nearestDistance(200)
            .build();

    @BeforeEach
    void setUp() {
        properties.setPhotoMaxWidthPx(800);
        when(repository.findById(1L)).thenReturn(Optional.of(sushi));
        service = new RestaurantDetailService(repository, matchService, placesClient, properties);
    }

    private void matched() {
        sushi.recordGoogleMatch(GoogleMatchStatus.MATCHED, PLACE_ID, LocalDateTime.now());
    }

    private static RestaurantPlace place() {
        Photo photo = new Photo(PHOTO, 4000, 3000,
                List.of(new AuthorAttribution("사진가", "https://maps.google.com/contrib/1", "https://lh3/a")));
        Review review = new Review("2주 전", 5, new LocalizedText("초밥이 훌륭해요", "ko"),
                new LocalizedText("寿司が素晴らしい", "ja"),
                new AuthorAttribution("작성자", "https://maps.google.com/contrib/x", "https://lh3/x"),
                "2026-09-20T10:00:00Z", "https://maps.google.com/review/x");
        return new RestaurantPlace(PLACE_ID, new LocalizedText("스시 이시카와", "ko"), "https://maps.google.com/?cid=9",
                null, new OpeningHours(List.of("월요일: 오후 6:00~오후 10:00")), 4.4, 812,
                "https://sushi-ishikawa.jp", List.of(photo), List.of(review),
                false, null, true, true, false,           // 아침·브런치·점심·저녁·채식
                null, true, null, null, null,             // 맥주·와인·칵테일·커피·디저트
                null, null, null, true, true, false, false, null,   // 어린이 메뉴·아이·단체·예약·매장·포장·배달·야외석
                new PriceRange(new Money("JPY", "10000", 0), new Money("JPY", "20000", 0)),
                new PaymentOptions(true, true, false, null));
    }

    // ── 상세 ──

    @Test
    void 같은_가게를_찾은_식당은_타베로그와_구글_정보를_나란히_준다() {
        matched();
        when(placesClient.restaurantDetails(PLACE_ID)).thenReturn(Optional.of(place()));

        RestaurantDetailResponseDto detail = service.details(1L);

        assertThat(detail.name()).isEqualTo("鮨いしかわ");
        assertThat(detail.tabelog().rating()).isEqualTo(3.85);
        assertThat(detail.tabelog().reviewCount()).isEqualTo(420);
        assertThat(detail.tabelog().holiday()).isEqualTo("日曜日");
        assertThat(detail.googleStatus()).isEqualTo(GoogleStatus.MATCHED);
        assertThat(detail.googleMessage()).isNull();
        assertThat(detail.google().rating()).isEqualTo(4.4);
        assertThat(detail.google().userRatingCount()).isEqualTo(812);
        assertThat(detail.google().localizedName()).isEqualTo("스시 이시카와");
        assertThat(detail.google().openingHours()).containsExactly("월요일: 오후 6:00~오후 10:00");
        assertThat(detail.google().photos()).hasSize(1);
        assertThat(detail.google().reviews().get(0).translated()).isTrue();
        assertThat(detail.attribution()).isEqualTo("Google Maps");   // 약관상 표시 의무
        assertThat(detail.reviewNotice()).isNotBlank();
        verifyNoInteractions(matchService);   // 이미 맞춰본 식당은 다시 검색하지 않는다
    }

    @Test
    void 식당_특성은_값과_true인_것의_한국어_이름을_준다() {
        matched();
        when(placesClient.restaurantDetails(PLACE_ID)).thenReturn(Optional.of(place()));

        RestaurantDetailResponseDto.FeaturesDto features = service.details(1L).google().features();

        assertThat(features.labels()).containsExactly("점심", "저녁", "와인", "예약 가능", "카드 결제");
        assertThat(features.servesBreakfast()).isFalse();      // 없음
        assertThat(features.servesBrunch()).isNull();          // 정보 없음
        assertThat(features.reservable()).isTrue();
        assertThat(features.acceptsCashOnly()).isFalse();
        assertThat(features.priceMinYen()).isEqualTo(10000);
        assertThat(features.priceMaxYen()).isEqualTo(20000);
    }

    @Test
    void 아직_맞춰보지_않은_식당은_먼저_맞춰본_뒤_보여준다() {
        doAnswer(inv -> {
            matched();
            return 0;
        }).when(matchService).verify(Set.of(1L));
        when(placesClient.restaurantDetails(PLACE_ID)).thenReturn(Optional.of(place()));

        RestaurantDetailResponseDto detail = service.details(1L);

        verify(matchService).verify(Set.of(1L));
        assertThat(detail.googleStatus()).isEqualTo(GoogleStatus.MATCHED);
        assertThat(detail.google().placeId()).isEqualTo(PLACE_ID);
    }

    @Test
    void 찾지_못한_식당은_타베로그_정보와_안내만_주고_구글_상세를_부르지_않는다() {
        sushi.recordGoogleMatch(GoogleMatchStatus.NOT_FOUND, null, LocalDateTime.now());

        RestaurantDetailResponseDto detail = service.details(1L);

        assertThat(detail.googleStatus()).isEqualTo(GoogleStatus.NOT_FOUND);
        assertThat(detail.googleMessage()).contains("찾지 못했어요");
        assertThat(detail.google()).isNull();
        assertThat(detail.attribution()).isNull();
        assertThat(detail.tabelog().rating()).isEqualTo(3.85);
        verify(placesClient, never()).restaurantDetails(anyString());
    }

    @Test
    void 폐업으로_나온_식당은_폐업_안내() {
        sushi.recordGoogleMatch(GoogleMatchStatus.CLOSED, PLACE_ID, LocalDateTime.now());

        RestaurantDetailResponseDto detail = service.details(1L);

        assertThat(detail.googleStatus()).isEqualTo(GoogleStatus.CLOSED);
        assertThat(detail.googleMessage()).contains("폐업");
        verify(placesClient, never()).restaurantDetails(anyString());
    }

    @Test
    void 맞춰보기에_실패하면_확인_못함으로_안내한다() {
        // verify가 Google 실패로 아무것도 기록하지 않은 경우 (상태가 계속 null)

        RestaurantDetailResponseDto detail = service.details(1L);

        assertThat(detail.googleStatus()).isEqualTo(GoogleStatus.UNCHECKED);
        assertThat(detail.googleMessage()).contains("잠시 후");
        assertThat(detail.google()).isNull();
    }

    @Test
    void 저장한_place_ID가_구글에서_사라졌으면_찾지_못함으로_안내한다() {
        matched();
        when(placesClient.restaurantDetails(PLACE_ID)).thenReturn(Optional.empty());

        RestaurantDetailResponseDto detail = service.details(1L);

        assertThat(detail.googleStatus()).isEqualTo(GoogleStatus.NOT_FOUND);
        assertThat(detail.google()).isNull();
    }

    @Test
    void 구글_상세_호출이_실패하면_503_예외를_그대로_던진다() {
        matched();
        when(placesClient.restaurantDetails(PLACE_ID)).thenThrow(new ExternalApiException("장소 정보를 불러오지 못했어요.", null));

        assertThatThrownBy(() -> service.details(1L)).isInstanceOf(ExternalApiException.class);
    }

    @Test
    void 없는_식당은_404() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.details(99L)).isInstanceOf(NotFoundException.class);
    }

    // ── 사진 ──

    @Test
    void 이_식당의_사진_이름으로_사진_주소를_받는다() {
        matched();
        when(placesClient.photoUri(PHOTO, 800)).thenReturn(Optional.of("https://lh3.googleusercontent.com/p"));

        assertThat(service.photo(1L, PHOTO, null).photoUri()).isEqualTo("https://lh3.googleusercontent.com/p");
    }

    @Test
    void 다른_장소의_사진은_400이고_구글을_부르지_않는다() {
        matched();

        assertThatThrownBy(() -> service.photo(1L, "places/OTHER/photos/abc", null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(placesClient, never()).photoUri(anyString(), anyInt());
    }

    @Test
    void 구글에서_찾지_못한_식당은_사진도_404() {
        sushi.recordGoogleMatch(GoogleMatchStatus.NOT_FOUND, null, LocalDateTime.now());

        assertThatThrownBy(() -> service.photo(1L, PHOTO, null)).isInstanceOf(NotFoundException.class);
        verify(placesClient, never()).photoUri(anyString(), anyInt());
    }

    @Test
    void 만료된_사진_이름은_404() {
        matched();
        when(placesClient.photoUri(any(), anyInt())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.photo(1L, PHOTO, 400))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("다시 불러와주세요");
    }
}
