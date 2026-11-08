package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.global.exception.ConflictException;
import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.itinerary.dictionary.PlaceTypeLabels;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.entity.ItinerarySource;
import com.example.japantravelplanner.itinerary.entity.ItineraryStop;
import com.example.japantravelplanner.itinerary.reason.ReasonContextLoader;
import com.example.japantravelplanner.itinerary.repository.ItineraryRepository;
import com.example.japantravelplanner.itinerary.repository.ItineraryStopRepository;
import com.example.japantravelplanner.restaurant.repository.RestaurantRepository;
import com.example.japantravelplanner.trip.entity.Trip;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.example.japantravelplanner.itinerary.service.ItineraryTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 11-08 리팩터링: 수정 서비스·생성 서비스가 복사해 쓰던 잠금·검사·새 버전 흐름 */
class ItineraryVersionsTest {

    private static final long TRIP_ID = 5L;

    private final ItineraryRepository itineraryRepository = mock(ItineraryRepository.class);
    private final ItineraryStopRepository stopRepository = mock(ItineraryStopRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final GenerationLock lock = new GenerationLock();
    private ItineraryVersions versions;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        versions = new ItineraryVersions(itineraryRepository, stopRepository, mock(RestaurantRepository.class),
                mock(PlaceTypeLabels.class), mock(ReasonContextLoader.class), lock, transactionManager);
    }

    @Test
    void 같은_여행을_고치는_중이면_409_끝나면_잠금을_푼다() {
        assertThat(lock.tryAcquire(TRIP_ID)).isTrue();
        assertThatThrownBy(() -> versions.locked(TRIP_ID, () -> "x"))
                .isInstanceOf(ConflictException.class).hasMessage(ItineraryVersions.BUSY_MESSAGE);
        lock.release(TRIP_ID);

        assertThatThrownBy(() -> versions.locked(TRIP_ID, () -> { throw new IllegalArgumentException("실패"); }))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(versions.locked(TRIP_ID, () -> "다시 됨")).isEqualTo("다시 됨");   // 실패해도 잠금이 풀렸다
    }

    @Test
    void 화면이_보던_버전이_최신이_아니면_409_보내지_않았으면_통과() {
        Itinerary latest = itinerary(3);
        ItineraryVersions.checkVersion(latest, null);
        ItineraryVersions.checkVersion(latest, 3);
        assertThatThrownBy(() -> ItineraryVersions.checkVersion(latest, 2))
                .isInstanceOf(ConflictException.class).hasMessage(ItineraryVersions.STALE_MESSAGE);
    }

    @Test
    void 다음_버전은_번호가_하나_크고_환율과_변경_메모를_가진다() {
        Itinerary latest = itinerary(3);
        Itinerary next = ItineraryVersions.next(latest.getTrip(), latest, ItinerarySource.EDITED, "DAY1 순서 변경");

        assertThat(next.getVersionNo()).isEqualTo(4);
        assertThat(next.getKrwPerYen()).isEqualTo(latest.getKrwPerYen());
        assertThat(next.getSource()).isEqualTo(ItinerarySource.EDITED);
        assertThat(next.getChangeNote()).isEqualTo("DAY1 순서 변경");
        assertThat(next.getDays()).isEmpty();
    }

    @Test
    void 다른_여행의_항목은_404_옛_버전의_항목은_409() throws Exception {
        Itinerary old = itinerary(1);
        setId(old, 10L);
        Itinerary latest = itinerary(2);
        setId(latest, 11L);
        when(itineraryRepository.findFirstByTripIdOrderByVersionNoDesc(TRIP_ID)).thenReturn(Optional.of(latest));
        ItineraryStop oldStop = old.getDays().get(0).getStops().get(0);
        when(stopRepository.findById(1L)).thenReturn(Optional.of(oldStop));

        assertThatThrownBy(() -> versions.latestStop(TRIP_ID, 1L))
                .isInstanceOf(ConflictException.class).hasMessage(ItineraryVersions.STALE_MESSAGE);
        assertThatThrownBy(() -> versions.latestStop(99L, 1L))   // 이 항목은 여행 5의 것
                .isInstanceOf(NotFoundException.class).hasMessage(ItineraryVersions.STOP_NOT_FOUND_MESSAGE);
        assertThat(versions.isInOldVersion(TRIP_ID, latest, 1L)).isTrue();

        ItineraryStop latestStop = latest.getDays().get(0).getStops().get(0);
        when(stopRepository.findById(2L)).thenReturn(Optional.of(latestStop));
        ItineraryVersions.LatestStop found = versions.latestStop(TRIP_ID, 2L);
        assertThat(found.stop()).isSameAs(latestStop);
        assertThat(found.latest()).isSameAs(latest);
        assertThat(versions.isInOldVersion(TRIP_ID, latest, 2L)).isFalse();
    }

    @Test
    void 일정이_없으면_404() {
        when(itineraryRepository.findFirstByTripIdOrderByVersionNoDesc(TRIP_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> versions.latest(TRIP_ID))
                .isInstanceOf(NotFoundException.class).hasMessage(ItineraryVersions.NO_ITINERARY_MESSAGE);
    }

    private static Itinerary itinerary(int versionNo) {
        Trip trip = trip();
        var park = attraction(11, "공원", "park");
        return ItineraryMapper.toEntity(trip, versionNo, 9.5,
                List.of(day(1, List.of(attractionStop(park, "10:00", "11:00", 60)), List.of(), List.of())),
                List.of(window("09:30", "22:00")), Map.of(), id -> park, id -> null);
    }

    private static void setId(Object entity, long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
