package com.example.japantravelplanner.trip.service;

import com.example.japantravelplanner.chat.repository.ChatMessageRepository;
import com.example.japantravelplanner.global.exception.ConflictException;
import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.global.exception.TooManyRequestsException;
import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.repository.ItineraryRepository;
import com.example.japantravelplanner.itinerary.service.GenerationLock;
import com.example.japantravelplanner.itinerary.service.ItineraryEditService;
import com.example.japantravelplanner.itinerary.service.ItineraryGenerationService;
import com.example.japantravelplanner.trip.dto.TripRequestDto;
import com.example.japantravelplanner.trip.dto.TripUpdateResponseDto;
import com.example.japantravelplanner.trip.dto.TripUpdateResponseDto.Result;
import com.example.japantravelplanner.trip.entity.TransportMode;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.entity.TripStatus;
import com.example.japantravelplanner.trip.repository.TripPreferenceRepository;
import com.example.japantravelplanner.trip.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.example.japantravelplanner.trip.service.TripChangeTest.sameAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 여행 조건 수정(바뀐 항목에 따라 일정 맞추기)과 삭제. 일정 계산 자체는 각 서비스 테스트에서 확인한다 */
class TripEditServiceTest {

    private static final long USER_ID = 1L;
    private static final long TRIP_ID = 5L;
    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final TripPreferenceRepository preferenceRepository = mock(TripPreferenceRepository.class);
    private final ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
    private final ItineraryRepository itineraryRepository = mock(ItineraryRepository.class);
    private final ItineraryGenerationService generationService = mock(ItineraryGenerationService.class);
    private final ItineraryEditService editService = mock(ItineraryEditService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final GenerationLock lock = new GenerationLock();

    private final Trip trip = TripChangeTest.trip();
    private final Itinerary latest = mock(Itinerary.class);
    private final ItineraryResponseDto newVersion = mock(ItineraryResponseDto.class);
    private TripEditService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(tripRepository.findByIdAndUserId(TRIP_ID, USER_ID)).thenReturn(Optional.of(trip));
        when(itineraryRepository.findFirstByTripIdOrderByVersionNoDesc(TRIP_ID)).thenReturn(Optional.of(latest));
        trip.setStatus(TripStatus.EDITING);
        trip.setTitle("도쿄 3박 4일");
        trip.setAccommodationLocation(GF.createPoint(new Coordinate(139.70, 35.69)));

        service = new TripEditService(tripRepository, preferenceRepository, chatMessageRepository,
                itineraryRepository, generationService, editService, lock, transactionManager);
    }

    // ── 수정: 바뀐 항목에 따라 ──

    @Test
    void 예산이_바뀌면_조건을_저장하고_일정을_새로_만든다() {
        TripRequestDto request = sameAs(trip);
        request.setBudget(900_000);
        when(generationService.generate(USER_ID, TRIP_ID)).thenReturn(newVersion);

        TripUpdateResponseDto response = service.update(USER_ID, TRIP_ID, request);

        assertThat(trip.getBudget()).isEqualTo(900_000);
        assertThat(response.changedFields()).containsExactly("budget");
        assertThat(response.itineraryUpdate().result()).isEqualTo(Result.REGENERATED);
        assertThat(response.itineraryUpdate().itinerary()).isSameAs(newVersion);
        verify(editService, never()).retimeForConditions(anyLong(), anyLong(), anyString(), anyBoolean());
    }

    @Test
    void 숙소_주소가_바뀌면_좌표를_비우고_장소는_그대로_시각만_다시_계산한다() {
        TripRequestDto request = sameAs(trip);
        request.setAccommodationAddress("東京都港区芝公園4-2-8");
        when(editService.retimeForConditions(eq(USER_ID), eq(TRIP_ID), anyString(), eq(false))).thenReturn(Optional.of(newVersion));

        TripUpdateResponseDto response = service.update(USER_ID, TRIP_ID, request);

        assertThat(trip.getAccommodationLocation()).isNull();   // 새 주소로 다시 찾게 한다
        assertThat(trip.getAccommodationAddress()).isEqualTo("東京都港区芝公園4-2-8");
        assertThat(response.itineraryUpdate().result()).isEqualTo(Result.RETIMED);
        assertThat(response.itineraryUpdate().itinerary()).isSameAs(newVersion);
        verify(editService).retimeForConditions(eq(USER_ID), eq(TRIP_ID), contains("숙소 주소"), eq(false));
        verify(generationService, never()).generate(anyLong(), anyLong());   // AI도, 생성 횟수도 쓰지 않는다
    }

    @Test
    void 완성_전_확인_대기에서_시각만_다시_계산하면_확인_대기를_유지하라고_넘긴다() {
        // 10-12 사용자 결정: AI로 새로 만들 때만 다시 확인 대기, 시각 재계산은 지금 상태 유지
        trip.setStatus(TripStatus.AWAITING_CONFIRMATION);
        TripRequestDto request = sameAs(trip);
        request.setTransportMode(TransportMode.RENTAL_CAR);
        when(editService.retimeForConditions(eq(USER_ID), eq(TRIP_ID), anyString(), eq(true))).thenReturn(Optional.of(newVersion));

        TripUpdateResponseDto response = service.update(USER_ID, TRIP_ID, request);

        assertThat(response.itineraryUpdate().result()).isEqualTo(Result.RETIMED);
        verify(editService).retimeForConditions(eq(USER_ID), eq(TRIP_ID), anyString(), eq(true));
    }

    @Test
    void 이동수단만_바꾸면_좌표는_그대로_두고_시각만_다시_계산한다() {
        TripRequestDto request = sameAs(trip);
        request.setTransportMode(TransportMode.RENTAL_CAR);
        when(editService.retimeForConditions(eq(USER_ID), eq(TRIP_ID), anyString(), eq(false))).thenReturn(Optional.of(newVersion));

        TripUpdateResponseDto response = service.update(USER_ID, TRIP_ID, request);

        assertThat(trip.getAccommodationLocation()).isNotNull();
        assertThat(trip.getTransportMode()).isEqualTo(TransportMode.RENTAL_CAR);
        assertThat(response.itineraryUpdate().result()).isEqualTo(Result.RETIMED);
        verify(editService).retimeForConditions(eq(USER_ID), eq(TRIP_ID), contains("이동수단"), eq(false));
    }

    @Test
    void 숙소_이름만_바꾸면_일정은_건드리지_않는다() {
        TripRequestDto request = sameAs(trip);
        request.setAccommodationName("호텔 새 이름");

        TripUpdateResponseDto response = service.update(USER_ID, TRIP_ID, request);

        assertThat(trip.getAccommodationName()).isEqualTo("호텔 새 이름");
        assertThat(trip.getStatus()).isEqualTo(TripStatus.EDITING);   // 상태도 그대로
        assertThat(response.itineraryUpdate().result()).isEqualTo(Result.NONE);
        verifyNoInteractions(generationService, editService);
    }

    @Test
    void 아직_일정이_없으면_조건만_저장한다() {
        when(itineraryRepository.findFirstByTripIdOrderByVersionNoDesc(TRIP_ID)).thenReturn(Optional.empty());
        trip.setStatus(TripStatus.PREFERENCE_GATHERING);
        TripRequestDto request = sameAs(trip);
        request.setTravelers(4);

        TripUpdateResponseDto response = service.update(USER_ID, TRIP_ID, request);

        assertThat(trip.getTravelers()).isEqualTo(4);
        assertThat(trip.getStatus()).isEqualTo(TripStatus.PREFERENCE_GATHERING);   // 대화 중이던 상태 그대로
        assertThat(response.itineraryUpdate().result()).isEqualTo(Result.NO_ITINERARY);
        verifyNoInteractions(generationService, editService);
    }

    @Test
    void 날짜가_바뀌면_제목도_다시_만든다() {
        TripRequestDto request = sameAs(trip);
        request.setEndDate(LocalDate.of(2026, 10, 21));
        when(generationService.generate(USER_ID, TRIP_ID)).thenReturn(newVersion);

        TripUpdateResponseDto response = service.update(USER_ID, TRIP_ID, request);

        assertThat(trip.getTitle()).isEqualTo("도쿄 1박 2일");
        assertThat(response.trip().getTitle()).isEqualTo("도쿄 1박 2일");
    }

    // ── 수정: 일정을 맞추지 못한 경우 ──

    @Test
    void 오늘_생성_횟수를_다_썼으면_조건은_저장하고_FAILED로_이유를_알린다() {
        TripRequestDto request = sameAs(trip);
        request.setBudget(900_000);
        when(generationService.generate(USER_ID, TRIP_ID))
                .thenThrow(new TooManyRequestsException("오늘은 이 여행의 일정을 더 만들 수 없어요. 내일 다시 시도해주세요."));

        TripUpdateResponseDto response = service.update(USER_ID, TRIP_ID, request);

        assertThat(trip.getBudget()).isEqualTo(900_000);
        assertThat(trip.getStatus()).isEqualTo(TripStatus.READY_TO_GENERATE);   // 화면에서 "다시 생성"을 보여줄 수 있다
        assertThat(response.itineraryUpdate().result()).isEqualTo(Result.FAILED);
        assertThat(response.itineraryUpdate().message()).contains("조건은 저장했지만").contains("내일 다시 시도");
        assertThat(response.itineraryUpdate().itinerary()).isNull();
    }

    @Test
    void 새_숙소_주소를_못_찾으면_조건은_저장하고_FAILED로_알린다() {
        TripRequestDto request = sameAs(trip);
        request.setAccommodationAddress("없는 주소");
        when(editService.retimeForConditions(eq(USER_ID), eq(TRIP_ID), anyString(), eq(false)))
                .thenThrow(new IllegalArgumentException("숙소 주소를 찾지 못했어요. 여행 조건에서 숙소 이름이나 주소를 확인해주세요."));

        TripUpdateResponseDto response = service.update(USER_ID, TRIP_ID, request);

        assertThat(trip.getAccommodationAddress()).isEqualTo("없는 주소");
        assertThat(trip.getStatus()).isEqualTo(TripStatus.READY_TO_GENERATE);
        assertThat(response.itineraryUpdate().result()).isEqualTo(Result.FAILED);
        assertThat(response.itineraryUpdate().message()).contains("숙소 주소를 찾지 못했어요");
    }

    @Test
    void 예상하지_못한_오류는_내부_내용을_보여주지_않는다() {
        TripRequestDto request = sameAs(trip);
        request.setBudget(900_000);
        when(generationService.generate(USER_ID, TRIP_ID)).thenThrow(new IllegalStateException("내부 오류 내용"));

        TripUpdateResponseDto response = service.update(USER_ID, TRIP_ID, request);

        assertThat(response.itineraryUpdate().result()).isEqualTo(Result.FAILED);
        assertThat(response.itineraryUpdate().message()).doesNotContain("내부 오류 내용");
    }

    // ── 수정: 막는 경우 ──

    @Test
    void 일정을_만들거나_고치는_중이면_409이고_아무것도_바꾸지_않는다() {
        TripRequestDto request = sameAs(trip);
        request.setBudget(900_000);
        lock.tryAcquire(TRIP_ID);

        assertThatThrownBy(() -> service.update(USER_ID, TRIP_ID, request)).isInstanceOf(ConflictException.class);
        assertThat(trip.getBudget()).isEqualTo(1_600_000);
    }

    @Test
    void 조건을_저장한_뒤에는_잠금을_풀어서_일정_생성이_잠금을_얻을_수_있다() {
        TripRequestDto request = sameAs(trip);
        request.setBudget(900_000);
        when(generationService.generate(USER_ID, TRIP_ID)).thenAnswer(inv -> {
            assertThat(lock.tryAcquire(TRIP_ID)).isTrue();   // 실제 생성 서비스도 같은 잠금을 쓴다
            lock.release(TRIP_ID);
            return newVersion;
        });

        assertThat(service.update(USER_ID, TRIP_ID, request).itineraryUpdate().result()).isEqualTo(Result.REGENERATED);
    }

    @Test
    void 남의_여행은_고칠_수_없다() {
        assertThatThrownBy(() -> service.update(999L, TRIP_ID, sameAs(trip))).isInstanceOf(NotFoundException.class);
        assertThat(lock.tryAcquire(TRIP_ID)).isTrue();   // 실패해도 잠금은 풀린다
    }

    // ── 삭제 ──

    @Test
    void 삭제하면_일정의_모든_버전과_대화_취향_여행을_지운다() {
        Itinerary v1 = mock(Itinerary.class);
        when(itineraryRepository.findByTripIdOrderByVersionNoDesc(TRIP_ID)).thenReturn(List.of(latest, v1));

        service.delete(USER_ID, TRIP_ID);

        InOrder order = inOrder(itineraryRepository, chatMessageRepository, preferenceRepository, tripRepository);
        order.verify(itineraryRepository).deleteAll(List.of(latest, v1));
        order.verify(chatMessageRepository).deleteByTripId(TRIP_ID);
        order.verify(preferenceRepository).deleteByTripId(TRIP_ID);
        order.verify(tripRepository).delete(trip);   // 여행을 가리키는 것들을 먼저 지우고 마지막에 여행
    }

    @Test
    void 남의_여행은_지울_수_없다() {
        assertThatThrownBy(() -> service.delete(999L, TRIP_ID)).isInstanceOf(NotFoundException.class);
        verify(tripRepository, never()).delete(any());
        verify(itineraryRepository, never()).deleteAll(any());
    }

    @Test
    void 일정을_만드는_중에는_지울_수_없다() {
        lock.tryAcquire(TRIP_ID);

        assertThatThrownBy(() -> service.delete(USER_ID, TRIP_ID)).isInstanceOf(ConflictException.class);
        verify(tripRepository, never()).delete(any());
    }
}
