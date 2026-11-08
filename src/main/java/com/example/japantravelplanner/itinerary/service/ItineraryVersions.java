package com.example.japantravelplanner.itinerary.service;

import com.example.japantravelplanner.global.exception.ConflictException;
import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.itinerary.dictionary.PlaceTypeLabels;
import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.entity.ItineraryDay;
import com.example.japantravelplanner.itinerary.entity.ItinerarySource;
import com.example.japantravelplanner.itinerary.entity.ItineraryStop;
import com.example.japantravelplanner.itinerary.reason.ReasonContext;
import com.example.japantravelplanner.itinerary.reason.ReasonContextLoader;
import com.example.japantravelplanner.itinerary.repository.ItineraryRepository;
import com.example.japantravelplanner.itinerary.repository.ItineraryStopRepository;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.restaurant.repository.RestaurantRepository;
import com.example.japantravelplanner.trip.entity.Trip;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 일정 버전을 다루는 공통 흐름 (11-08 리팩터링).
 * 생성·식당 교체·관광지 교체·항목 편집·되돌리기·조건 재계산이 각자 복사해 쓰던 것을 모았다:
 * 잠금 + 쓰기 트랜잭션, 최신 버전 찾기, 옛 화면 막기, 다음 버전 만들기, 저장(버전 충돌만 409), 응답 변환.
 *
 * 스프링 빈이 아니라 각 서비스가 자기 의존성으로 만들어 쓴다 (기존 생성자·테스트를 그대로 두려고).
 * 항목을 다루지 않는 일정 생성은 stopRepository 없이(null) 만든다.
 */
@Slf4j
public final class ItineraryVersions {

    /** Itinerary의 (여행, 버전 번호) 유일 제약 이름 */
    static final String VERSION_UNIQUE_CONSTRAINT = "uk_itineraries_trip_version";

    static final String BUSY_MESSAGE = "이미 일정을 만들거나 고치고 있어요. 잠시만 기다려주세요.";
    static final String STALE_MESSAGE = "일정이 바뀌었어요. 화면을 새로고침해주세요.";
    static final String NO_ITINERARY_MESSAGE = "아직 만들어진 일정이 없습니다.";
    static final String STOP_NOT_FOUND_MESSAGE = "일정 항목을 찾을 수 없습니다.";

    private final ItineraryRepository itineraryRepository;
    private final ItineraryStopRepository stopRepository;
    private final RestaurantRepository restaurantRepository;
    private final PlaceTypeLabels typeLabels;
    private final ReasonContextLoader reasonContextLoader;
    private final GenerationLock lock;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;

    public ItineraryVersions(ItineraryRepository itineraryRepository, ItineraryStopRepository stopRepository,
                             RestaurantRepository restaurantRepository, PlaceTypeLabels typeLabels,
                             ReasonContextLoader reasonContextLoader, GenerationLock lock,
                             PlatformTransactionManager transactionManager) {
        this.itineraryRepository = itineraryRepository;
        this.stopRepository = stopRepository;
        this.restaurantRepository = restaurantRepository;
        this.typeLabels = typeLabels;
        this.reasonContextLoader = reasonContextLoader;
        this.lock = lock;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    // ===================== 트랜잭션·잠금 =====================

    public <T> T read(Supplier<T> work) {
        return readTransaction.execute(status -> work.get());
    }

    /** 같은 여행을 동시에 고치지 못하게 잠그고, 쓰기 트랜잭션 안에서 실행한다 */
    public <T> T locked(Long tripId, Supplier<T> work) {
        return locked(tripId, BUSY_MESSAGE, () -> writeTransaction.execute(status -> work.get()));
    }

    /** 잠금만 (트랜잭션 밖 작업이 섞인 흐름: 식당 교체 전 Google 확인, 일정 생성의 AI 호출 등) */
    public <T> T locked(Long tripId, String busyMessage, Supplier<T> work) {
        if (!lock.tryAcquire(tripId)) {
            throw new ConflictException(busyMessage);
        }
        try {
            return work.get();
        } finally {
            lock.release(tripId);
        }
    }

    public <T> T write(Supplier<T> work) {
        return writeTransaction.execute(status -> work.get());
    }

    // ===================== 불러오기·검사 =====================

    public Itinerary latest(Long tripId) {
        return itineraryRepository.findFirstByTripIdOrderByVersionNoDesc(tripId)
                .orElseThrow(() -> new NotFoundException(NO_ITINERARY_MESSAGE));
    }

    /** 화면이 보고 있는 버전을 보냈는데 최신이 아니면, 옛 화면 기준으로 고치지 않게 막는다 */
    public static void checkVersion(Itinerary latest, Integer version) {
        if (version != null && version != latest.getVersionNo()) {
            throw new ConflictException(STALE_MESSAGE);
        }
    }

    public static ItineraryDay dayOf(Itinerary itinerary, int dayNumber) {
        return itinerary.getDays().stream()
                .filter(d -> d.getDayNumber() == dayNumber)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("해당 날짜의 일정을 찾을 수 없습니다."));
    }

    /**
     * 이 여행의 최신 버전에 있는 항목. 다른 여행의 항목이면 404(있는지도 알려주지 않는다),
     * 이 여행의 옛 버전 항목이면 409(화면이 옛 일정을 보고 있다).
     */
    public LatestStop latestStop(Long tripId, Long stopId) {
        Objects.requireNonNull(stopRepository, "항목을 다루는 서비스는 ItineraryStopRepository 가 필요하다");
        ItineraryStop stop = stopRepository.findById(stopId)
                .orElseThrow(() -> new NotFoundException(STOP_NOT_FOUND_MESSAGE));
        ItineraryDay day = stop.getDay();
        Itinerary itinerary = day.getItinerary();
        if (!Objects.equals(tripId, itinerary.getTrip().getId())) {
            throw new NotFoundException(STOP_NOT_FOUND_MESSAGE);
        }
        Itinerary latest = latest(tripId);
        if (!Objects.equals(latest.getId(), itinerary.getId())) {
            throw new ConflictException(STALE_MESSAGE);
        }
        return new LatestStop(latest, day, stop);
    }

    /** 이 여행의 옛 버전에 있던 항목인지 (화면이 옛 일정을 보고 있는 경우) */
    public boolean isInOldVersion(Long tripId, Itinerary latest, Long stopId) {
        return stopRepository.findById(stopId)
                .map(s -> s.getDay().getItinerary())
                .filter(i -> Objects.equals(i.getTrip().getId(), tripId) && !Objects.equals(i.getId(), latest.getId()))
                .isPresent();
    }

    // ===================== 새 버전 =====================

    /** latest 다음 번호의 빈 새 버전 (환율은 latest 것을 이어받는다: 같은 일정 안에서는 같은 환율) */
    public static Itinerary next(Trip trip, Itinerary latest, ItinerarySource source, String note) {
        return Itinerary.create(trip, latest.getVersionNo() + 1, latest.getKrwPerYen(), source, note)
                .copyExchangeRateFrom(latest);
    }

    public void save(Itinerary next) {
        save(next, "일정이 동시에 바뀌었어요. 화면을 새로고침해주세요.");
    }

    /**
     * 새 버전을 저장한다. 같은 버전 번호로 동시에 저장하려 한 경우(유일 제약)만 409로 안내한다
     * (잠금은 서버 한 대 안에서만 막아준다). 그 밖의 DB 거절(허용 값 제약 등)은 동시성 문제가 아니므로
     * 원인을 로그에 남기고 그대로 던진다. 전부 409로 바꾸면 진짜 원인이 가려진다 (10-06 되돌리기 409 사고).
     */
    public void save(Itinerary next, String conflictMessage) {
        try {
            itineraryRepository.saveAndFlush(next);
        } catch (DataIntegrityViolationException e) {
            Throwable cause = e.getMostSpecificCause();
            String message = cause.getMessage() == null ? "" : cause.getMessage();
            if (message.contains(VERSION_UNIQUE_CONSTRAINT)) {
                throw new ConflictException(conflictMessage);
            }
            log.error("[Itinerary] 새 버전 저장이 DB에서 거절됨 (동시성 문제가 아님): {}", message);
            throw e;
        }
    }

    // ===================== 응답 =====================

    public ItineraryResponseDto toResponse(Itinerary itinerary) {
        return toResponse(itinerary, reasonContextLoader.load(itinerary.getTrip()));
    }

    /** 식당 대안 후보는 id만 저장되어 있어서, 일정 전체의 대안을 한 번에 조회해서 풀어준다 */
    public ItineraryResponseDto toResponse(Itinerary itinerary, ReasonContext reasons) {
        Set<Long> ids = ItineraryResponseDto.alternativeRestaurantIds(itinerary);
        Map<Long, Restaurant> alternatives = ids.isEmpty() ? Map.of()
                : restaurantRepository.findAllById(ids).stream()
                  .collect(Collectors.toMap(Restaurant::getId, Function.identity()));
        return ItineraryResponseDto.from(itinerary, typeLabels::toVisibleLabels, alternatives, reasons);
    }

    /** 최신 버전에 있는 항목 하나와 그 날 */
    public record LatestStop(Itinerary latest, ItineraryDay day, ItineraryStop stop) {
    }
}
