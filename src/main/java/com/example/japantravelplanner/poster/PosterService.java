package com.example.japantravelplanner.poster;

import com.example.japantravelplanner.global.exception.ConflictException;
import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.global.exception.TooManyRequestsException;
import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto;
import com.example.japantravelplanner.itinerary.naming.KoreanNameService;
import com.example.japantravelplanner.itinerary.repository.ItineraryRepository;
import com.example.japantravelplanner.itinerary.service.GenerationLock;
import com.example.japantravelplanner.itinerary.service.ItineraryGenerationService;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.repository.TripRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 여행 포스터 (10-12). 1장 = 일러스트 지도 포스터, 2장부터 = 상세 일정표 (모두 이미지 AI).
 * 한 장에 수십 초가 걸려서 요청은 바로 202로 돌려주고 따로 그린다 → 화면은 GET으로 상태를 확인.
 * 그림 내용(제목·DAY·장소 이름·시각)은 서버가 일정에서 정하고, AI는 그대로 그리기만 한다 (PosterPromptBuilder).
 * 이미지 AI 호출은 트랜잭션 밖. 여행당 24시간 dailyLimit번.
 */
@Slf4j
@Service
public class PosterService {

    private final TripRepository tripRepository;
    private final ItineraryRepository itineraryRepository;
    private final TripPosterRepository posterRepository;
    private final ItineraryGenerationService generationService;
    private final KoreanNameService koreanNames;
    private final PosterImageClient imageClient;
    private final PosterStorage storage;
    private final PosterProperties props;
    private final GenerationLock lock;
    private final Executor executor;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;

    public PosterService(TripRepository tripRepository, ItineraryRepository itineraryRepository,
                         TripPosterRepository posterRepository, ItineraryGenerationService generationService,
                         KoreanNameService koreanNames, PosterImageClient imageClient, PosterStorage storage,
                         PosterProperties props, GenerationLock lock,
                         @Qualifier("posterExecutor") Executor executor,
                         PlatformTransactionManager transactionManager) {
        this.tripRepository = tripRepository;
        this.itineraryRepository = itineraryRepository;
        this.posterRepository = posterRepository;
        this.generationService = generationService;
        this.koreanNames = koreanNames;
        this.imageClient = imageClient;
        this.storage = storage;
        this.props = props;
        this.lock = lock;
        this.executor = executor;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    // ===================== 만들기 =====================

    public PosterResponseDto start(Long userId, Long tripId) {
        if (!props.isEnabled()) throw new ConflictException("지금은 포스터를 만들 수 없어요.");
        if (!lock.tryAcquire(tripId)) {
            throw new ConflictException("일정을 만들거나 고치는 중이에요. 끝난 뒤에 다시 시도해주세요.");
        }
        TripPoster poster;
        try {
            Trip trip = readTransaction.execute(status -> {
                Trip t = findTrip(userId, tripId);
                posterRepository.findFirstByTripIdOrderByIdDesc(tripId)
                        .filter(p -> p.getStatus() == PosterStatus.GENERATING && !isStale(p))
                        .ifPresent(p -> {
                            throw new ConflictException("포스터를 그리는 중이에요. 다 그려진 뒤에 다시 만들 수 있어요.");
                        });
                if (remaining(tripId) <= 0) {
                    throw new TooManyRequestsException("오늘은 이 여행의 포스터를 더 만들 수 없어요. 내일 다시 시도해주세요.");
                }
                return t;
            });
            // 한국어 이름이 비어 있으면 채운다 (AI, 트랜잭션 밖). 일정이 없으면 404
            ItineraryResponseDto itinerary = koreanNames.fill(generationService.getLatest(userId, tripId));
            List<PosterPromptBuilder.PageSpec> specs = PosterPromptBuilder.build(
                    itinerary, trip.getTitle(), trip.getStartDate(), props);

            poster = writeTransaction.execute(status -> {
                TripPoster p = TripPoster.start(findTrip(userId, tripId), itinerary.version(), props.getModel(),
                        LocalDateTime.now());
                specs.forEach(spec -> p.addPage(TripPosterPage.of(spec.pageNo(), spec.kind(), spec.size(), spec.prompt())));
                return posterRepository.save(p);
            });
        } finally {
            lock.release(tripId);
        }

        long posterId = poster.getId();
        log.info("[Poster] 시작 tripId={} posterId={} 일정 v{} {}장", tripId, posterId, poster.getItineraryVersion(),
                poster.getPages().size());
        List<CompletableFuture<Void>> jobs = poster.getPages().stream()
                .map(page -> CompletableFuture.runAsync(
                        () -> render(tripId, posterId, page.getPageNo(), page.getPrompt(), page.getSize()), executor))
                .toList();
        CompletableFuture.allOf(jobs.toArray(CompletableFuture[]::new)).whenComplete((r, e) -> finish(posterId));
        return get(userId, tripId);
    }

    private void render(long tripId, long posterId, int pageNo, String prompt, String size) {
        try {
            byte[] png = imageClient.generate(prompt, size);
            String path = storage.save(tripId, posterId, pageNo, png);
            updatePage(posterId, pageNo, page -> page.done(path));
        } catch (RuntimeException e) {
            log.warn("[Poster] {}번 포스터 {}장 실패: {}", posterId, pageNo, e.getMessage());
            updatePage(posterId, pageNo, TripPosterPage::failed);
        }
    }

    private void updatePage(long posterId, int pageNo, java.util.function.Consumer<TripPosterPage> change) {
        writeTransaction.executeWithoutResult(status -> posterRepository.findById(posterId).ifPresent(p ->
                p.getPages().stream().filter(page -> page.getPageNo() == pageNo).findFirst().ifPresent(change)));
    }

    private void finish(long posterId) {
        writeTransaction.executeWithoutResult(status -> posterRepository.findById(posterId).ifPresent(p -> {
            long done = p.getPages().stream().filter(page -> page.getStatus() == PosterStatus.DONE).count();
            if (done == p.getPages().size()) {
                p.finish(PosterStatus.DONE, null, LocalDateTime.now());
            } else {
                p.finish(PosterStatus.FAILED, done == 0 ? "포스터를 만들지 못했어요. 잠시 후 다시 시도해주세요."
                        : "일부 장을 만들지 못했어요. 다시 만들어 주세요.", LocalDateTime.now());
            }
            log.info("[Poster] 끝 posterId={} {} ({}/{}장)", posterId, p.getStatus(), done, p.getPages().size());
        }));
    }

    // ===================== 조회 =====================

    /** 가장 최근 포스터 (그리는 중이면 장마다 상태) */
    public PosterResponseDto get(Long userId, Long tripId) {
        return writeTransaction.execute(status -> {
            findTrip(userId, tripId);
            TripPoster poster = posterRepository.findFirstByTripIdOrderByIdDesc(tripId)
                    .orElseThrow(() -> new NotFoundException("아직 만든 포스터가 없어요."));
            if (poster.getStatus() == PosterStatus.GENERATING && isStale(poster)) {
                // 서버가 다시 시작되는 등으로 끝나지 못한 묶음
                poster.getPages().stream().filter(page -> page.getStatus() == PosterStatus.GENERATING)
                        .forEach(TripPosterPage::failed);
                poster.finish(PosterStatus.FAILED, "포스터를 끝까지 만들지 못했어요. 다시 만들어 주세요.", LocalDateTime.now());
            }
            return toDto(poster);
        });
    }

    /** 다 그려진 장의 PNG */
    public byte[] image(Long userId, Long tripId, Long posterId, int pageNo) {
        String path = readTransaction.execute(status -> {
            findTrip(userId, tripId);
            TripPoster poster = posterRepository.findByIdAndTripId(posterId, tripId)
                    .orElseThrow(() -> new NotFoundException("포스터를 찾을 수 없어요."));
            return poster.getPages().stream()
                    .filter(page -> page.getPageNo() == pageNo && page.getStatus() == PosterStatus.DONE)
                    .map(TripPosterPage::getFilePath).findFirst()
                    .orElseThrow(() -> new NotFoundException("아직 그려지지 않은 장이에요."));
        });
        return storage.read(path);
    }

    private PosterResponseDto toDto(TripPoster poster) {
        Long tripId = poster.getTrip().getId();
        int latestVersion = itineraryRepository.findFirstByTripIdOrderByVersionNoDesc(tripId)
                .map(i -> i.getVersionNo()).orElse(poster.getItineraryVersion());
        List<PosterResponseDto.PageDto> pages = poster.getPages().stream()
                .map(page -> new PosterResponseDto.PageDto(page.getPageNo(), page.getKind(), page.getStatus(), page.getSize(),
                        page.getStatus() == PosterStatus.DONE
                                ? "/api/trips/" + tripId + "/posters/" + poster.getId() + "/pages/" + page.getPageNo() + "/image"
                                : null))
                .toList();
        return new PosterResponseDto(poster.getId(), tripId, poster.getStatus(), poster.getItineraryVersion(),
                latestVersion != poster.getItineraryVersion(), poster.getErrorMessage(), poster.getCreatedAt(),
                poster.getFinishedAt(), remaining(tripId), pages);
    }

    private int remaining(Long tripId) {
        long used = posterRepository.countByTripIdAndCreatedAtAfter(tripId, LocalDateTime.now().minusHours(24));
        return (int) Math.max(0, props.getDailyLimit() - used);
    }

    private boolean isStale(TripPoster poster) {
        return poster.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(props.getStaleMinutes()));
    }

    private Trip findTrip(Long userId, Long tripId) {
        return tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new NotFoundException("여행을 찾을 수 없습니다."));
    }
}
