package com.example.japantravelplanner.attraction.service;

import com.example.japantravelplanner.attraction.dto.AttractionDetailResponseDto;
import com.example.japantravelplanner.attraction.dto.AttractionDetailResponseDto.ReviewDto;
import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.attraction.repository.AttractionRepository;
import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.global.google.GooglePlacesClient;
import com.example.japantravelplanner.global.google.GooglePlacesClient.AuthorAttribution;
import com.example.japantravelplanner.global.google.GooglePlacesClient.LocalizedText;
import com.example.japantravelplanner.global.google.GooglePlacesClient.OpeningHours;
import com.example.japantravelplanner.global.google.GooglePlacesClient.Photo;
import com.example.japantravelplanner.global.google.GooglePlacesClient.PlaceDetails;
import com.example.japantravelplanner.global.google.GooglePlacesClient.Review;
import com.example.japantravelplanner.global.google.GoogleProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 관광지 상세: Google Places 응답을 화면용으로 바꾸고, 사진 요청은 이 장소의 사진만 허용한다 */
class AttractionDetailServiceTest {

    private static final String PLACE_ID = "ChIJPyOTG8KMGGARh_IXobWxHmo";
    private static final String PHOTO = "places/" + PLACE_ID + "/photos/AelY_CvAbc-123";

    private final AttractionRepository attractionRepository = mock(AttractionRepository.class);
    private final GooglePlacesClient placesClient = mock(GooglePlacesClient.class);
    private final GoogleProperties properties = new GoogleProperties();
    private AttractionDetailService service;

    private final Attraction gyoen = Attraction.builder().id(64L).name("新宿御苑").placeId(PLACE_ID).build();

    @BeforeEach
    void setUp() {
        properties.setPhotoMaxWidthPx(800);
        when(attractionRepository.findById(64L)).thenReturn(Optional.of(gyoen));
        service = new AttractionDetailService(attractionRepository, placesClient, properties);
    }

    private static Photo photo(int i) {
        return new Photo("places/" + PLACE_ID + "/photos/p" + i, 4000, 3000,
                List.of(new AuthorAttribution("사진가" + i, "https://maps.google.com/contrib/" + i, "https://lh3/a" + i)));
    }

    private static Review review(String text, String textLang, String original, String originalLang) {
        return new Review("3주 전", 5, new LocalizedText(text, textLang), new LocalizedText(original, originalLang),
                new AuthorAttribution("작성자", "https://maps.google.com/contrib/x", "https://lh3/x"),
                "2026-09-15T10:00:00Z", "https://maps.google.com/review/x");
    }

    private PlaceDetails place(List<Photo> photos, List<Review> reviews) {
        return new PlaceDetails(PLACE_ID, new LocalizedText("신주쿠 교엔", "ko"), "https://maps.google.com/?cid=1",
                new LocalizedText("넓은 잔디밭과 일본 정원이 있는 국립 정원", "ko"),
                new OpeningHours(List.of("월요일: 휴무일", "화요일: 오전 9:00~오후 4:30")),
                4.6, 45733, "https://www.env.go.jp/garden/shinjukugyoen/", photos, reviews);
    }

    // ── 상세 ──

    @Test
    void 상세는_구글_정보를_화면용으로_바꾸고_출처를_붙인다() {
        when(placesClient.details(PLACE_ID)).thenReturn(Optional.of(place(List.of(photo(1)), List.of())));

        AttractionDetailResponseDto detail = service.details(64L);

        assertThat(detail.attractionId()).isEqualTo(64L);
        assertThat(detail.name()).isEqualTo("新宿御苑");               // 일정에 나오는 이름 그대로
        assertThat(detail.localizedName()).isEqualTo("신주쿠 교엔");
        assertThat(detail.summary()).isEqualTo("넓은 잔디밭과 일본 정원이 있는 국립 정원");
        assertThat(detail.openingHours()).containsExactly("월요일: 휴무일", "화요일: 오전 9:00~오후 4:30");
        assertThat(detail.rating()).isEqualTo(4.6);
        assertThat(detail.userRatingCount()).isEqualTo(45733);
        assertThat(detail.googleMapsUri()).isEqualTo("https://maps.google.com/?cid=1");
        assertThat(detail.attribution()).isEqualTo("Google Maps");    // 약관상 표시 의무
        assertThat(detail.reviewNotice()).isNotBlank();

        assertThat(detail.photos()).hasSize(1);
        assertThat(detail.photos().get(0).name()).isEqualTo("places/" + PLACE_ID + "/photos/p1");
        assertThat(detail.photos().get(0).authors().get(0).name()).isEqualTo("사진가1");
    }

    @Test
    void 사진은_최대_5장까지만_준다() {
        List<Photo> seven = IntStream.rangeClosed(1, 7).mapToObj(AttractionDetailServiceTest::photo).toList();
        when(placesClient.details(PLACE_ID)).thenReturn(Optional.of(place(seven, List.of())));

        assertThat(service.details(64L).photos()).hasSize(5);
    }

    @Test
    void 번역된_리뷰는_번역_표시와_원문을_함께_준다() {
        when(placesClient.details(PLACE_ID)).thenReturn(Optional.of(place(List.of(), List.of(
                review("벚꽃이 정말 예뻐요", "ko", "桜がとても綺麗です", "ja"),
                review("산책하기 좋아요", "ko", "산책하기 좋아요", "ko")))));

        List<ReviewDto> reviews = service.details(64L).reviews();

        assertThat(reviews.get(0).text()).isEqualTo("벚꽃이 정말 예뻐요");
        assertThat(reviews.get(0).translated()).isTrue();
        assertThat(reviews.get(0).originalText()).isEqualTo("桜がとても綺麗です");
        assertThat(reviews.get(0).originalLanguage()).isEqualTo("ja");
        assertThat(reviews.get(0).author().name()).isEqualTo("작성자");
        assertThat(reviews.get(0).relativeTime()).isEqualTo("3주 전");

        assertThat(reviews.get(1).translated()).isFalse();      // 처음부터 한국어로 쓴 리뷰
        assertThat(reviews.get(1).originalText()).isNull();
    }

    @Test
    void 사진_리뷰_영업시간_소개가_없어도_빈_값으로_준다() {
        when(placesClient.details(PLACE_ID)).thenReturn(Optional.of(new PlaceDetails(PLACE_ID, null, null, null,
                null, null, null, null, null, null)));

        AttractionDetailResponseDto detail = service.details(64L);

        assertThat(detail.photos()).isEmpty();
        assertThat(detail.reviews()).isEmpty();
        assertThat(detail.openingHours()).isEmpty();
        assertThat(detail.summary()).isNull();
        assertThat(detail.localizedName()).isNull();
        assertThat(detail.name()).isEqualTo("新宿御苑");
    }

    @Test
    void 없는_관광지_place_id가_없는_관광지_구글에_없는_장소는_404() {
        when(attractionRepository.findById(1L)).thenReturn(Optional.empty());
        when(attractionRepository.findById(2L)).thenReturn(Optional.of(Attraction.builder().id(2L).name("이름만").build()));
        when(placesClient.details(PLACE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.details(1L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.details(2L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.details(64L)).isInstanceOf(NotFoundException.class);
    }

    // ── 사진 ──

    @Test
    void 사진_이름으로_사진_주소를_받는다() {
        when(placesClient.photoUri(PHOTO, 800)).thenReturn(Optional.of("https://lh3.googleusercontent.com/photo"));

        assertThat(service.photo(64L, PHOTO, null).photoUri()).isEqualTo("https://lh3.googleusercontent.com/photo");
    }

    @Test
    void 사진_크기는_범위_안이면_그대로_벗어나면_기본값() {
        when(placesClient.photoUri(anyString(), anyInt())).thenReturn(Optional.of("https://lh3/x"));

        service.photo(64L, PHOTO, 400);
        service.photo(64L, PHOTO, 5000);
        service.photo(64L, PHOTO, 10);

        verify(placesClient).photoUri(PHOTO, 400);
        verify(placesClient, times(2)).photoUri(PHOTO, 800);
    }

    @Test
    void 다른_장소의_사진이나_이상한_이름은_400이고_구글을_부르지_않는다() {
        assertThatThrownBy(() -> service.photo(64L, "places/OTHER_PLACE/photos/abc", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.photo(64L, "places/" + PLACE_ID + "/photos/../../other", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.photo(64L, "https://evil.example.com/x", null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(placesClient, never()).photoUri(anyString(), anyInt());
    }

    @Test
    void 만료된_사진_이름은_404로_다시_불러오라고_알린다() {
        when(placesClient.photoUri(PHOTO, 800)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.photo(64L, PHOTO, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("다시 불러와주세요");
    }
}
