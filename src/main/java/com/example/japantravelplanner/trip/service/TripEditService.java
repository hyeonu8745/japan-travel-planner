package com.example.japantravelplanner.trip.service;

import com.example.japantravelplanner.chat.repository.ChatMessageRepository;
import com.example.japantravelplanner.global.exception.AiServiceException;
import com.example.japantravelplanner.global.exception.ConflictException;
import com.example.japantravelplanner.global.exception.ExternalApiException;
import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.global.exception.TooManyRequestsException;
import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.repository.ItineraryRepository;
import com.example.japantravelplanner.itinerary.service.GenerationLock;
import com.example.japantravelplanner.itinerary.service.ItineraryEditService;
import com.example.japantravelplanner.itinerary.service.ItineraryGenerationService;
import com.example.japantravelplanner.poster.PosterStorage;
import com.example.japantravelplanner.poster.TripPoster;
import com.example.japantravelplanner.poster.TripPosterRepository;
import com.example.japantravelplanner.trip.dto.TripRequestDto;
import com.example.japantravelplanner.trip.dto.TripResponseDto;
import com.example.japantravelplanner.trip.dto.TripUpdateResponseDto;
import com.example.japantravelplanner.trip.dto.TripUpdateResponseDto.ItineraryUpdate;
import com.example.japantravelplanner.trip.dto.TripUpdateResponseDto.Result;
import com.example.japantravelplanner.trip.entity.Airport;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.entity.TripStatus;
import com.example.japantravelplanner.trip.repository.TripPreferenceRepository;
import com.example.japantravelplanner.trip.repository.TripRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 여행 조건 수정과 삭제.
 *
 * 수정은 세 단계다. (일정 생성처럼 AI 호출 동안 DB 연결을 붙잡지 않는다)
 *  ① 조건 저장 (트랜잭션): 바뀐 항목을 판단하고 저장한다. 일정이 있고 영향이 있으면 상태를 READY_TO_GENERATE로
 *  ② 일정 맞추기 (트랜잭션 밖): 바뀐 항목에 따라 시각만 다시 계산하거나(RETIME) 일정을 새로 만든다(REGENERATE)
 *  ③ 결과 응답: 일정을 맞추지 못해도 조건은 저장된 채로 두고, 이유를 안내한다 (상태가 READY_TO_GENERATE로 남아
 *     화면에서 "다시 생성"을 보여줄 수 있다)
 *
 * 삭제는 대화·취향·일정의 모든 버전을 함께 지운다. 되돌릴 수 없다.
 */
@Slf4j
@Service
public class TripEditService {

    private final TripRepository tripRepository;
    private final TripPreferenceRepository preferenceRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ItineraryRepository itineraryRepository;
    private final ItineraryGenerationService generationService;
    private final ItineraryEditService editService;
    private final GenerationLock lock;
    private final TripPosterRepository posterRepository;
    private final PosterStorage posterStorage;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;

    public TripEditService(TripRepository tripRepository,
                           TripPreferenceRepository preferenceRepository,
                           ChatMessageRepository chatMessageRepository,
                           ItineraryRepository itineraryRepository,
                           ItineraryGenerationService generationService,
                           ItineraryEditService editService,
                           GenerationLock lock,
                           TripPosterRepository posterRepository,
                           PosterStorage posterStorage,
                           PlatformTransactionManager transactionManager) {
        this.tripRepository = tripRepository;
        this.preferenceRepository = preferenceRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.itineraryRepository = itineraryRepository;
        this.generationService = generationService;
        this.editService = editService;
        this.lock = lock;
        this.posterRepository = posterRepository;
        this.posterStorage = posterStorage;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    // ===================== 수정 =====================

    public TripUpdateResponseDto update(Long userId, Long tripId, TripRequestDto request) {
        // ① 조건 저장 (일정 생성·교체와 겹치지 않게 같은 잠금을 쓴다)
        if (!lock.tryAcquire(tripId)) {
            throw new ConflictException("이미 일정을 만들거나 고치고 있어요. 잠시 후 다시 시도해주세요.");
        }
        Saved saved;
        try {
            saved = writeTransaction.execute(status -> save(userId, tripId, request));
        } finally {
            lock.release(tripId);
        }

        // ② 일정 맞추기
        ItineraryUpdate update = syncItinerary(userId, tripId, saved);

        // ③ 상태가 바뀌었을 수 있으니 다시 읽어서 돌려준다
        TripResponseDto trip = readTransaction.execute(status -> TripResponseDto.from(TripAccess.owned(tripRepository, userId, tripId)));
        return new TripUpdateResponseDto(trip, saved.change().fields(), update);
    }

    private Saved save(Long userId, Long tripId, TripRequestDto request) {
        Trip trip = TripAccess.owned(tripRepository, userId, tripId);
        TripChange change = TripChange.between(trip, request);
        boolean hasItinerary = itineraryRepository.findFirstByTripIdOrderByVersionNoDesc(tripId).isPresent();
        boolean wasAwaiting = trip.isAwaitingConfirmation();   // 완성 전이었는지 (시각만 다시 계산하면 그대로 둔다)

        if (change.changed("startDate") || change.changed("endDate")) {
            trip.setTitle(TripService.defaultTitle(request.getStartDate(), request.getEndDate()));
        }
        if (change.changed("accommodationAddress")) {
            trip.setAccommodationLocation(null);   // 새 주소로 다시 찾게 한다
        }
        trip.setStartDate(request.getStartDate());
        trip.setEndDate(request.getEndDate());
        trip.setTravelers(request.getTravelers());
        trip.setBudget(request.getBudget());
        trip.setTravelPace(request.getTravelPace());
        trip.setInterests(request.getInterests() == null ? new ArrayList<>() : new ArrayList<>(request.getInterests()));
        trip.setAllergies(request.getAllergies());
        trip.setMobilityConstraints(request.getMobilityConstraints());
        trip.setTransportMode(request.getTransportMode());
        trip.setAccommodationName(request.getAccommodationName());
        trip.setAccommodationAddress(request.getAccommodationAddress());
        trip.setArrivalAirport(Airport.normalize(request.getArrivalAirport()));
        trip.setArrivalFlightTime(request.getArrivalFlightTime());
        trip.setDepartureAirport(Airport.normalize(request.getDepartureAirport()));
        trip.setDepartureFlightTime(request.getDepartureFlightTime());

        // 일정이 조건과 맞지 않게 됐다. 맞추는 데 성공하면 GENERATED/EDITING으로 바뀐다
        if (hasItinerary && change.impact() != TripChange.Impact.NONE) {
            trip.setStatus(TripStatus.READY_TO_GENERATE);
        }

        log.info("[Trip] 조건 수정 tripId={} 바뀐 항목={} 영향={}", tripId, change.fields(), change.impact());
        return new Saved(change, hasItinerary, wasAwaiting);
    }

    private ItineraryUpdate syncItinerary(Long userId, Long tripId, Saved saved) {
        TripChange change = saved.change();
        if (!saved.hasItinerary()) {
            return new ItineraryUpdate(Result.NO_ITINERARY, "조건을 저장했어요. 아직 만든 일정이 없어요.", null);
        }
        if (change.impact() == TripChange.Impact.NONE) {
            String message = change.fields().isEmpty() ? "바뀐 내용이 없어요." : "일정에 영향을 주는 조건은 바뀌지 않았어요.";
            return new ItineraryUpdate(Result.NONE, message, null);
        }

        try {
            if (change.impact() == TripChange.Impact.RETIME) {
                String note = "여행 조건 변경(" + change.labels() + "): 장소는 그대로, 시각 다시 계산";
                ItineraryResponseDto itinerary = editService.retimeForConditions(userId, tripId, note, saved.wasAwaiting()).orElse(null);
                return new ItineraryUpdate(Result.RETIMED,
                        "장소는 그대로 두고 이동 시간과 시각을 다시 계산했어요.", itinerary);
            }
            ItineraryResponseDto itinerary = generationService.generate(userId, tripId);
            return new ItineraryUpdate(Result.REGENERATED,
                    "바뀐 조건으로 일정을 새로 만들었어요. 이전 일정은 변경 이력에 남아 있어요.", itinerary);
        } catch (TooManyRequestsException | ConflictException | IllegalArgumentException
                 | ExternalApiException | AiServiceException | NotFoundException e) {
            // 사용자에게 보여줄 수 있는 이유 (횟수 제한, 숙소 주소 못 찾음, 외부 서비스 문제 등)
            log.warn("[Trip] 조건은 저장했지만 일정을 맞추지 못함 tripId={}: {}", tripId, e.getMessage());
            return failed(e.getMessage());
        } catch (RuntimeException e) {
            log.error("[Trip] 조건 변경 후 일정 맞추기 실패 tripId={}", tripId, e);
            return failed("잠시 후 일정 화면에서 다시 만들어주세요.");
        }
    }

    private static ItineraryUpdate failed(String reason) {
        return new ItineraryUpdate(Result.FAILED, "조건은 저장했지만 일정을 다시 만들지 못했어요. " + reason, null);
    }

    // ===================== 삭제 =====================

    /** 여행과 대화·취향·일정의 모든 버전을 함께 지운다 */
    public void delete(Long userId, Long tripId) {
        if (!lock.tryAcquire(tripId)) {
            throw new ConflictException("일정을 만들거나 고치는 중이에요. 끝난 뒤에 다시 시도해주세요.");
        }
        try {
            List<String> posterFiles = new ArrayList<>();
            writeTransaction.executeWithoutResult(status -> {
                Trip trip = TripAccess.owned(tripRepository, userId, tripId);
                // 포스터(10-12)도 함께. 파일은 DB 삭제가 끝난 뒤 지운다
                List<TripPoster> posters = posterRepository.findByTripId(tripId);
                posters.forEach(p -> p.getPages().forEach(page -> posterFiles.add(page.getFilePath())));
                posterRepository.deleteAll(posters);
                List<Itinerary> versions = itineraryRepository.findByTripIdOrderByVersionNoDesc(tripId);
                itineraryRepository.deleteAll(versions);   // 날짜·항목·대안·빠진 장소까지 함께 지워진다
                chatMessageRepository.deleteByTripId(tripId);
                preferenceRepository.deleteByTripId(tripId);
                tripRepository.delete(trip);
                log.info("[Trip] 여행 삭제 tripId={} (일정 {}개 버전 포함)", tripId, versions.size());
            });
            posterFiles.forEach(posterStorage::deleteQuietly);
        } finally {
            lock.release(tripId);
        }
    }


    /** ① 단계 결과 */
    private record Saved(TripChange change, boolean hasItinerary, boolean wasAwaiting) {
    }
}
