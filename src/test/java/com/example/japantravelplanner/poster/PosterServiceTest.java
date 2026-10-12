package com.example.japantravelplanner.poster;

import com.example.japantravelplanner.global.exception.AiServiceException;
import com.example.japantravelplanner.global.exception.ConflictException;
import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.global.exception.TooManyRequestsException;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.naming.KoreanNameService;
import com.example.japantravelplanner.itinerary.repository.ItineraryRepository;
import com.example.japantravelplanner.itinerary.service.GenerationLock;
import com.example.japantravelplanner.itinerary.service.ItineraryGenerationService;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static com.example.japantravelplanner.poster.PosterFixtures.itinerary;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 여행 포스터 만들기·조회 (10-12). 그리기는 바로 실행되는 실행기로 테스트한다 */
class PosterServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long TRIP_ID = 9L;

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final ItineraryRepository itineraryRepository = mock(ItineraryRepository.class);
    private final TripPosterRepository posterRepository = mock(TripPosterRepository.class);
    private final ItineraryGenerationService generationService = mock(ItineraryGenerationService.class);
    private final KoreanNameService koreanNames = mock(KoreanNameService.class);
    private final PosterImageClient imageClient = mock(PosterImageClient.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final GenerationLock lock = new GenerationLock();
    private final PosterProperties props = new PosterProperties();
    private final Trip trip = Trip.builder().id(TRIP_ID).title("도쿄 2박 3일").startDate(LocalDate.of(2026, 10, 25)).build();
    private final AtomicReference<TripPoster> saved = new AtomicReference<>();

    @TempDir
    Path dir;
    private PosterStorage storage;
    private PosterService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(tripRepository.findByIdAndUserId(TRIP_ID, USER_ID)).thenReturn(Optional.of(trip));
        when(generationService.getLatest(USER_ID, TRIP_ID)).thenReturn(itinerary(3));
        when(koreanNames.fill(any())).thenAnswer(inv -> inv.getArgument(0));
        when(imageClient.generate(anyString(), anyString())).thenReturn(new byte[]{9, 9});
        when(posterRepository.save(any())).thenAnswer(inv -> {
            TripPoster p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 5L);
            saved.set(p);
            return p;
        });
        when(posterRepository.findById(5L)).thenAnswer(inv -> Optional.ofNullable(saved.get()));
        when(posterRepository.findByIdAndTripId(5L, TRIP_ID)).thenAnswer(inv -> Optional.ofNullable(saved.get()));
        when(posterRepository.findFirstByTripIdOrderByIdDesc(TRIP_ID)).thenAnswer(inv -> Optional.ofNullable(saved.get()));
        Itinerary latest = mock(Itinerary.class);
        when(latest.getVersionNo()).thenReturn(3);
        when(itineraryRepository.findFirstByTripIdOrderByVersionNoDesc(TRIP_ID)).thenReturn(Optional.of(latest));

        props.setStorageDir(dir.toString());
        storage = new PosterStorage(props);
        service = new PosterService(tripRepository, itineraryRepository, posterRepository, generationService,
                koreanNames, imageClient, storage, props, lock, Runnable::run, transactionManager);
    }

    @Test
    void 지도_포스터와_상세_일정표를_그려_파일로_저장하고_다_되면_DONE() {
        PosterResponseDto response = service.start(USER_ID, TRIP_ID);

        assertThat(response.status()).isEqualTo(PosterStatus.DONE);
        assertThat(response.itineraryVersion()).isEqualTo(3);
        assertThat(response.stale()).isFalse();
        assertThat(response.pages()).extracting(PosterResponseDto.PageDto::kind)
                .containsExactly(PosterPageKind.COVER, PosterPageKind.DETAIL, PosterPageKind.DETAIL);   // 3일 → 상세 2장
        assertThat(response.pages().get(0).imageUrl()).isEqualTo("/api/trips/9/posters/5/pages/1/image");
        verify(imageClient, times(3)).generate(anyString(), anyString());
        assertThat(service.image(USER_ID, TRIP_ID, 5L, 2)).containsExactly(9, 9);
        assertThat(lock.tryAcquire(TRIP_ID)).isTrue();
    }

    @Test
    void 한_장이라도_실패하면_FAILED이고_그려진_장은_볼_수_있다() {
        when(imageClient.generate(anyString(), anyString()))
                .thenReturn(new byte[]{1})
                .thenThrow(new AiServiceException("실패", null))
                .thenReturn(new byte[]{3});

        PosterResponseDto response = service.start(USER_ID, TRIP_ID);

        assertThat(response.status()).isEqualTo(PosterStatus.FAILED);
        assertThat(response.errorMessage()).contains("일부 장");
        assertThat(response.pages()).extracting(PosterResponseDto.PageDto::status)
                .containsExactly(PosterStatus.DONE, PosterStatus.FAILED, PosterStatus.DONE);
        assertThatThrownBy(() -> service.image(USER_ID, TRIP_ID, 5L, 2)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void 하루_한도를_넘으면_429_그리는_중이면_409() {
        when(posterRepository.countByTripIdAndCreatedAtAfter(eq(TRIP_ID), any())).thenReturn(3L);
        assertThatThrownBy(() -> service.start(USER_ID, TRIP_ID)).isInstanceOf(TooManyRequestsException.class);

        when(posterRepository.countByTripIdAndCreatedAtAfter(eq(TRIP_ID), any())).thenReturn(0L);
        saved.set(TripPoster.start(trip, 3, "gpt-image-2", LocalDateTime.now()));
        assertThatThrownBy(() -> service.start(USER_ID, TRIP_ID)).isInstanceOf(ConflictException.class);
        verifyNoInteractions(imageClient);
        assertThat(lock.tryAcquire(TRIP_ID)).isTrue();   // 실패해도 잠금은 풀린다
    }

    @Test
    void 일정이_바뀌면_stale_오래_GENERATING이면_실패로_정리() {
        service.start(USER_ID, TRIP_ID);
        Itinerary newer = mock(Itinerary.class);
        when(newer.getVersionNo()).thenReturn(4);
        when(itineraryRepository.findFirstByTripIdOrderByVersionNoDesc(TRIP_ID)).thenReturn(Optional.of(newer));
        assertThat(service.get(USER_ID, TRIP_ID).stale()).isTrue();

        saved.set(TripPoster.start(trip, 4, "gpt-image-2", LocalDateTime.now().minusMinutes(30)));
        assertThat(service.get(USER_ID, TRIP_ID).status()).isEqualTo(PosterStatus.FAILED);
    }

    @Test
    void 남의_여행_포스터는_볼_수_없다() {
        assertThatThrownBy(() -> service.get(99L, TRIP_ID)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.image(99L, TRIP_ID, 5L, 1)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.start(99L, TRIP_ID)).isInstanceOf(NotFoundException.class);
    }
}
