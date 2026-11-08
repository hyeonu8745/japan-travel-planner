package com.example.japantravelplanner.weather;

import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.repository.TripRepository;
import com.example.japantravelplanner.trip.service.TripAccess;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 여행 날짜별 날씨 (10-16). 저장하지 않고 메모리에 잠깐 캐시한다.
 * - 오늘(일본 날짜) − 92일 ~ 오늘 + 15일: 예보 API (지나간 날도 같은 API가 실제 값을 준다)
 * - 그 밖(보통 한 달 뒤 여행): 작년 같은 날의 실제 날씨 (archive API) — "이맘때는 이랬어요" 참고용
 * 위치는 숙소 좌표, 아직 없으면(일정을 만들기 전) 도쿄역. Open-Meteo 호출은 트랜잭션 밖.
 */
@Slf4j
@Service
public class WeatherService {

    static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    static final double TOKYO_LAT = 35.6812, TOKYO_LNG = 139.7671;

    private final TripRepository tripRepository;
    private final WeatherClient client;
    private final WeatherProperties props;
    private final Clock clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    @Autowired   // 생성자가 두 개(테스트용 시계)라 Spring이 쓸 생성자를 지정한다 (10-11 사고 사례)
    public WeatherService(TripRepository tripRepository, WeatherClient client, WeatherProperties props) {
        this(tripRepository, client, props, Clock.system(JST));
    }

    WeatherService(TripRepository tripRepository, WeatherClient client, WeatherProperties props, Clock clock) {
        this.tripRepository = tripRepository;
        this.client = client;
        this.props = props;
        this.clock = clock;
    }

    public WeatherResponseDto get(Long userId, Long tripId) {
        Trip trip = TripAccess.owned(tripRepository, userId, tripId);
        Point hotel = trip.getAccommodationLocation();
        double lat = hotel == null ? TOKYO_LAT : hotel.getY();
        double lng = hotel == null ? TOKYO_LNG : hotel.getX();
        return build(tripId, trip.getStartDate(), trip.getEndDate(), lat, lng);
    }

    WeatherResponseDto build(Long tripId, LocalDate start, LocalDate end, double lat, double lng) {
        if (!props.isEnabled() || start == null || end == null) {
            return new WeatherResponseDto(tripId, "NONE", "날씨 정보를 불러오지 못했어요.", List.of(), WeatherResponseDto.ATTRIBUTION);
        }
        LocalDate today = LocalDate.now(clock.withZone(JST));
        LocalDate forecastFrom = today.minusDays(props.getPastDays());
        LocalDate forecastTo = today.plusDays(props.getForecastDays());

        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) dates.add(d);
        List<LocalDate> forecastDates = dates.stream().filter(d -> !d.isBefore(forecastFrom) && !d.isAfter(forecastTo)).toList();
        List<LocalDate> archiveDates = dates.stream().filter(d -> !forecastDates.contains(d)).toList();

        Map<LocalDate, DailyWeather> forecast = forecastDates.isEmpty() ? Map.of()
                : fetch("F", lat, lng, forecastDates.get(0), forecastDates.get(forecastDates.size() - 1), true);
        // 작년 같은 날 (2월 29일은 28일로)
        Map<LocalDate, DailyWeather> lastYear = archiveDates.isEmpty() ? Map.of()
                : fetch("A", lat, lng, archiveDates.get(0).minusYears(1),
                archiveDates.get(archiveDates.size() - 1).minusYears(1), false);

        List<WeatherResponseDto.DayDto> days = new ArrayList<>();
        int forecastCount = 0, archiveCount = 0;
        for (int i = 0; i < dates.size(); i++) {
            LocalDate date = dates.get(i);
            boolean isForecast = forecastDates.contains(date);
            DailyWeather w = isForecast ? forecast.get(date) : lastYear.get(date.minusYears(1));
            if (w == null) {
                days.add(new WeatherResponseDto.DayDto(i + 1, date, "NONE", null, null, WeatherCondition.UNKNOWN.name(),
                        WeatherCondition.UNKNOWN.label(), null, null, null, null, false, false));
                continue;
            }
            if (isForecast) forecastCount++; else archiveCount++;
            days.add(toDay(i + 1, date, w, isForecast));
        }
        String source = forecastCount + archiveCount == 0 ? "NONE"
                : archiveCount == 0 ? "FORECAST" : forecastCount == 0 ? "LAST_YEAR" : "MIXED";
        return new WeatherResponseDto(tripId, source, message(source), days, WeatherResponseDto.ATTRIBUTION);
    }

    WeatherResponseDto.DayDto toDay(int dayNumber, LocalDate date, DailyWeather w, boolean forecast) {
        WeatherCondition condition = WeatherCondition.of(w.weatherCode());
        boolean rainy = forecast
                ? (w.rainProbability() != null && w.rainProbability() >= props.getRainProbabilityPercent())
                  || (condition.wet() && condition != WeatherCondition.SNOW)
                : (w.rainMm() != null && w.rainMm() >= props.getRainMillimeters())
                  || condition == WeatherCondition.RAIN || condition == WeatherCondition.THUNDER;
        return new WeatherResponseDto.DayDto(dayNumber, date, forecast ? "FORECAST" : "LAST_YEAR", w.date(),
                w.weatherCode(), condition.name(), condition.label(), w.tempMax(), w.tempMin(),
                forecast ? w.rainProbability() : null, w.rainMm(), rainy, forecast && rainy);
    }

    static String message(String source) {
        return switch (source) {
            case "FORECAST" -> "일기예보예요. 날씨는 바뀔 수 있으니 출발 전에 다시 확인해 주세요.";
            case "LAST_YEAR" -> "아직 예보가 없어 작년 같은 날 날씨를 보여드려요. 출발 16일 전부터 실제 예보로 바뀌어요.";
            case "MIXED" -> "앞쪽 날짜는 일기예보, 뒤쪽 날짜는 작년 같은 날 날씨예요.";
            default -> "날씨 정보를 불러오지 못했어요. 잠시 후 다시 확인해 주세요.";
        };
    }

    private Map<LocalDate, DailyWeather> fetch(String kind, double lat, double lng, LocalDate from, LocalDate to,
                                               boolean forecast) {
        String key = kind + ":" + WeatherClient.round(lat) + "," + WeatherClient.round(lng) + ":" + from + ":" + to;
        Instant now = clock.instant();
        Cached hit = cache.get(key);
        if (hit != null && hit.expiresAt().isAfter(now)) return hit.byDate();

        Optional<List<DailyWeather>> result = forecast
                ? client.forecast(lat, lng, from, to) : client.archive(lat, lng, from, to);
        if (result.isEmpty()) return Map.of();   // 실패는 캐시하지 않는다 (다음 조회 때 다시)
        Map<LocalDate, DailyWeather> byDate = new HashMap<>();
        result.get().forEach(w -> byDate.put(w.date(), w));
        Duration ttl = forecast ? Duration.ofMinutes(props.getForecastCacheMinutes())
                : Duration.ofHours(props.getArchiveCacheHours());
        cache.put(key, new Cached(Map.copyOf(byDate), now.plus(ttl)));
        if (cache.size() > 2000) cache.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
        return byDate;
    }

    private record Cached(Map<LocalDate, DailyWeather> byDate, Instant expiresAt) {
    }
}
