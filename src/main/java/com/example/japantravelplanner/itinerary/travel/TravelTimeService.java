package com.example.japantravelplanner.itinerary.travel;

import com.example.japantravelplanner.global.exception.ExternalApiException;
import com.example.japantravelplanner.global.google.GoogleRoutesClient;
import com.example.japantravelplanner.global.google.GoogleRoutesClient.RouteElement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 장소 목록의 이동 시간 표를 만든다.
 *
 * 기본은 자체 계산 (Google Routes는 일본 대중교통을 제공하지 않고, 이동 시간 캐시는 약관상 허용되지 않음)
 *  - 도보 시간이 기준 이내면 도보
 *  - 넘으면 대중교통 모델과 비교해 빠른 쪽
 *  - 렌트카는 운전 모델
 * use-google-routes를 켜면 도보 시간만 Google에서 실시간으로 받는다 (저장하지 않음)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TravelTimeService {

    private final TravelProperties props;
    private final GoogleRoutesClient routesClient;

    /**
     * @param baseMode 여행의 이동수단 (TRANSIT 또는 DRIVE)
     * @param walkThresholdMinutes 이 시간 이내는 걸어감 (여유 속도·이동 제약이면 더 짧게)
     */
    public TravelMatrix buildMatrix(List<TravelPoint> points, LegMode baseMode, int walkThresholdMinutes) {
        int n = points.size();
        TravelLeg[][] legs = new TravelLeg[n][n];

        Map<String, RouteElement> googleWalk = (props.isUseGoogleRoutes() && baseMode == LegMode.TRANSIT)
                ? fetchGoogleWalk(points)
                : Map.of();

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                double straight = straightDistance(points.get(i), points.get(j));

                if (baseMode == LegMode.DRIVE) {
                    legs[i][j] = driveLeg(straight);
                    continue;
                }

                TravelLeg walk = walkLeg(straight, googleWalk.get(i + ">" + j));
                if (walk.minutes() <= walkThresholdMinutes) {
                    legs[i][j] = walk;
                } else {
                    TravelLeg transit = transitLeg(straight);
                    legs[i][j] = transit.minutes() < walk.minutes() ? transit : walk;
                }
            }
        }
        return new TravelMatrix(points, legs);
    }

    /** Google 도보 경로를 실시간으로 (저장하지 않음). 실패하면 빈 Map → 자체 계산 */
    private Map<String, RouteElement> fetchGoogleWalk(List<TravelPoint> points) {
        try {
            Map<String, RouteElement> result = new HashMap<>();
            for (RouteElement e : routesClient.computeMatrix(points, points, LegMode.WALK)) {
                result.put(e.originIndex() + ">" + e.destinationIndex(), e);
            }
            return result;
        } catch (ExternalApiException e) {
            log.warn("[Travel] Google 도보 경로 실패, 자체 계산으로 대체합니다");
            return Map.of();
        }
    }

    private TravelLeg walkLeg(double straight, RouteElement google) {
        if (google != null) {
            int minutes = Math.max(1, (int) Math.ceil(google.seconds() / 60.0));
            return new TravelLeg(minutes, google.distanceMeters(), LegMode.WALK, false);
        }
        double distance = straight * props.getWalkDetour();
        int minutes = Math.max(1, (int) Math.ceil(distance / props.getWalkMetersPerMinute()));
        return new TravelLeg(minutes, (int) distance, LegMode.WALK, false);
    }

    private TravelLeg transitLeg(double straight) {
        double distance = straight * props.getTransitDetour();
        int minutes = (int) Math.ceil(distance / props.getTransitMetersPerMinute()) + props.getTransitOverheadMinutes();
        return new TravelLeg(minutes, (int) distance, LegMode.TRANSIT, false);
    }

    private TravelLeg driveLeg(double straight) {
        double distance = straight * props.getTransitDetour();
        int minutes = (int) Math.ceil(distance / props.getDriveMetersPerMinute()) + props.getDriveOverheadMinutes();
        return new TravelLeg(minutes, (int) distance, LegMode.DRIVE, false);
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