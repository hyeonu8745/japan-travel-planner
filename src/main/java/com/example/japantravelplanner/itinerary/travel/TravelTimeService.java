package com.example.japantravelplanner.itinerary.travel;

import com.example.japantravelplanner.global.exception.ExternalApiException;
import com.example.japantravelplanner.global.google.GoogleRoutesClient;
import com.example.japantravelplanner.global.google.GoogleRoutesClient.RouteElement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 장소 목록의 이동 시간 표를 만든다.
 * 순서: 800m 이내는 도보 → 캐시 → Routes API → 실패하면 직선거리 추정
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TravelTimeService {

    private static final double WALK_THRESHOLD_M = 800;
    private static final double WALK_M_PER_MIN = 80;          // 도보 분속
    private static final double TRANSIT_M_PER_MIN = 333;      // 대중교통 평균 (시속 20km, 환승 포함)
    private static final double DRIVE_M_PER_MIN = 416;        // 도심 운전 평균 (시속 25km)
    private static final double DETOUR_FACTOR = 1.3;          // 실제 길은 직선보다 돌아감
    private static final int CACHE_DAYS = 30;

    private final GoogleRoutesClient routesClient;
    private final TravelTimeCacheRepository cacheRepository;

    /**
     * @param baseMode 여행의 이동수단 (TRANSIT 또는 DRIVE)
     * @param departureTime 대중교통 시간표 기준 시각
     */
    public TravelMatrix buildMatrix(List<TravelPoint> points, LegMode baseMode, Instant departureTime) {
        int n = points.size();
        TravelLeg[][] legs = new TravelLeg[n][n];
        List<int[]> needApi = new ArrayList<>();

        // ① 가까운 곳은 도보, ② 캐시 확인
        Map<String, TravelTimeCache> cached = loadCache(points, baseMode);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                double straight = straightDistance(points.get(i), points.get(j));
                if (straight <= WALK_THRESHOLD_M && baseMode == LegMode.TRANSIT) {
                    legs[i][j] = walkLeg(straight);
                    continue;
                }
                TravelTimeCache hit = cached.get(points.get(i).cacheKey() + ">" + points.get(j).cacheKey());
                if (hit != null) {
                    legs[i][j] = new TravelLeg(hit.getMinutes(), hit.getDistanceMeters(), baseMode, false);
                } else {
                    needApi.add(new int[]{i, j});
                }
            }
        }

        // ③ 남은 구간만 API로
        if (!needApi.isEmpty()) {
            fillFromApi(points, baseMode, departureTime, legs, needApi);
        }

        // ④ 그래도 빈 칸(API 실패, 경로 없음)은 직선거리로 추정
        int estimatedCount = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j && legs[i][j] == null) {
                    legs[i][j] = estimatedLeg(straightDistance(points.get(i), points.get(j)), baseMode);
                    estimatedCount++;
                }
            }
        }

        log.info("[Travel] 장소 {}개, API 요청 구간 {}개, 추정 구간 {}개", n, needApi.size(), estimatedCount);
        return new TravelMatrix(points, legs);
    }

    private Map<String, TravelTimeCache> loadCache(List<TravelPoint> points, LegMode mode) {
        Set<String> keys = new HashSet<>();
        points.forEach(p -> keys.add(p.cacheKey()));
        Map<String, TravelTimeCache> result = new HashMap<>();
        cacheRepository.findByModeAndOriginKeyInAndDestinationKeyInAndCreatedAtAfter(
                        mode, keys, keys, LocalDateTime.now().minusDays(CACHE_DAYS))
                .forEach(c -> result.put(c.getOriginKey() + ">" + c.getDestinationKey(), c));
        return result;
    }

    private void fillFromApi(List<TravelPoint> points, LegMode mode, Instant departureTime,
                             TravelLeg[][] legs, List<int[]> needApi) {
        List<RouteElement> elements;
        try {
            // 행렬 API는 출발지 × 도착지 전체를 한 번에 계산 (필요한 칸만 골라 쓴다)
            elements = routesClient.computeMatrix(points, points, mode, departureTime);
        } catch (ExternalApiException e) {
            log.warn("[Travel] Routes API 실패, 직선거리로 추정합니다");
            return;   // 빈 칸은 ④에서 추정
        }

        Set<String> needed = new HashSet<>();
        needApi.forEach(p -> needed.add(p[0] + ">" + p[1]));

        List<TravelTimeCache> toSave = new ArrayList<>();
        for (RouteElement e : elements) {
            int i = e.originIndex();
            int j = e.destinationIndex();
            if (i == j || !needed.contains(i + ">" + j)) continue;

            int minutes = Math.max(1, (int) Math.ceil(e.seconds() / 60.0));
            legs[i][j] = new TravelLeg(minutes, e.distanceMeters(), mode, false);
            toSave.add(new TravelTimeCache(points.get(i).cacheKey(), points.get(j).cacheKey(),
                    mode, minutes, e.distanceMeters()));
        }

        try {
            cacheRepository.saveAll(toSave);
        } catch (DataIntegrityViolationException ignored) {
            // 동시에 같은 구간을 저장한 경우. 이미 있으니 무시
        }
    }

    private TravelLeg walkLeg(double straight) {
        double distance = straight * 1.25;
        return new TravelLeg(Math.max(1, (int) Math.ceil(distance / WALK_M_PER_MIN)),
                (int) distance, LegMode.WALK, false);
    }

    private TravelLeg estimatedLeg(double straight, LegMode mode) {
        double distance = straight * DETOUR_FACTOR;
        double speed = mode == LegMode.DRIVE ? DRIVE_M_PER_MIN : TRANSIT_M_PER_MIN;
        int waiting = mode == LegMode.DRIVE ? 5 : 8;   // 주차, 역까지 걷기·대기
        return new TravelLeg((int) Math.ceil(distance / speed) + waiting, (int) distance, mode, true);
    }

    static double straightDistance(TravelPoint a, TravelPoint b) {
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLng = Math.toRadians(b.lng() - a.lng());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * 6_371_000 * Math.asin(Math.sqrt(h));
    }
}