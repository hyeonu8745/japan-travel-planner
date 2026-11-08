package com.example.japantravelplanner.itinerary.naming;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.attraction.repository.AttractionRepository;
import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.restaurant.repository.RestaurantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 장소·식당의 한국어 이름 (10-11 사용자 결정 (가)): 일정에 처음 쓰일 때 AI로 한 번 만들어 DB에 저장하고,
 * 그 뒤로는 저장된 이름을 쓴다. 화면은 "한국어 + 작게 원래 이름".
 *  - Google 한국어 이름은 쓰지 않는다: 볼 때마다 요금이 들고 약관상 저장할 수 없다
 *  - 9,399곳을 미리 다 만들지 않는다: 일정에 들어간 곳(고른 곳 + 식당 다음 후보)만
 *  - AI 호출은 트랜잭션 밖, 저장만 짧은 트랜잭션 (원칙 2)
 *  - 실패해도 일정은 정상 (원래 이름으로 보임). 실패한 이름은 retryAfterMinutes 동안 다시 묻지 않는다
 */
@Slf4j
@Service
public class KoreanNameService {

    private final KoreanNameTranslator translator;
    private final AttractionRepository attractionRepository;
    private final RestaurantRepository restaurantRepository;
    private final KoreanNameProperties props;
    private final TransactionTemplate writeTransaction;
    private final Clock clock;
    /** 실패한 키 → 다시 물어도 되는 시각 */
    private final Map<String, Instant> failedUntil = new ConcurrentHashMap<>();

    /** @param clock 서버 시계 (ClockConfig). 실패한 이름을 다시 묻지 않는 시간 계산에 쓴다 */
    public KoreanNameService(KoreanNameTranslator translator, AttractionRepository attractionRepository,
                             RestaurantRepository restaurantRepository, KoreanNameProperties props,
                             PlatformTransactionManager transactionManager, Clock clock) {
        this.translator = translator;
        this.attractionRepository = attractionRepository;
        this.restaurantRepository = restaurantRepository;
        this.props = props;
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * 일정 생성용: 이름이 없는 장소·식당의 한국어 이름을 만들어 저장하고, 넘겨받은 객체에도 넣는다
     * (하루 설명 AI가 같은 이름을 쓰게 하려고 설명보다 먼저 부른다). 트랜잭션 밖에서 부른다
     */
    public void ensure(Collection<Attraction> attractions, Collection<Restaurant> restaurants) {
        Map<String, String> missing = new LinkedHashMap<>();
        Map<String, Attraction> attractionByKey = new HashMap<>();
        Map<String, Restaurant> restaurantByKey = new HashMap<>();
        attractions.stream().filter(a -> a != null && a.getNameKo() == null).forEach(a -> {
            missing.put(key('a', a.getId()), a.getName());
            attractionByKey.put(key('a', a.getId()), a);
        });
        restaurants.stream().filter(r -> r != null && r.getNameKo() == null).forEach(r -> {
            missing.put(key('r', r.getId()), r.getName());
            restaurantByKey.put(key('r', r.getId()), r);
        });

        Map<String, String> made = translateAndSave(missing);
        made.forEach((k, name) -> {
            if (attractionByKey.containsKey(k)) attractionByKey.get(k).setNameKo(name);
            if (restaurantByKey.containsKey(k)) restaurantByKey.get(k).setNameKo(name);
        });
    }

    /**
     * 일정 응답용: 이름이 비어 있는 항목(예전에 만든 일정, 교체·추가로 새로 들어온 곳)을 채운 응답.
     * 비어 있는 곳이 없으면 AI를 부르지 않고 그대로 돌려준다. 트랜잭션 밖에서 부른다
     */
    public ItineraryResponseDto fill(ItineraryResponseDto response) {
        if (response == null) return null;
        Map<String, String> missing = new LinkedHashMap<>();
        response.days().forEach(day -> day.stops().forEach(stop -> {
            if (stop.nameKo() == null) {
                if (stop.attraction() != null) missing.put(key('a', stop.attraction().id()), stop.name());
                if (stop.restaurant() != null) missing.put(key('r', stop.restaurant().id()), stop.name());
            }
            stop.alternatives().stream().filter(alt -> alt.nameKo() == null)
                    .forEach(alt -> missing.put(key('r', alt.id()), alt.name()));
        }));
        if (missing.isEmpty()) return response;

        Map<String, String> made = translateAndSave(missing);
        if (made.isEmpty()) return response;
        Map<Long, String> attractionNames = new HashMap<>();
        Map<Long, String> restaurantNames = new HashMap<>();
        made.forEach((k, name) -> (k.charAt(0) == 'a' ? attractionNames : restaurantNames)
                .put(Long.parseLong(k.substring(1)), name));
        return response.withKoreanNames(attractionNames, restaurantNames);
    }

    /**
     * 후보 몇 곳의 한국어 이름 (채팅 수정에서 "이렇게 바꿀까요?"를 묻기 전, 10-11).
     * 후보 목록 API는 이름을 만들지 않아서 迎賓館赤坂離宮처럼 원래 이름으로 물어봤다. 만든 이름은 저장된다
     * @param attractions 관광지 id → 원래 이름 (이름이 이미 있는 곳은 넘기지 않는다)
     * @param restaurants 식당 id → 원래 이름
     * @return "a12"·"r34" 키 대신 종류별 id → 한국어 이름 (만들지 못한 곳은 빠진다)
     */
    public Names namesFor(Map<Long, String> attractions, Map<Long, String> restaurants) {
        Map<String, String> missing = new LinkedHashMap<>();
        attractions.forEach((id, name) -> missing.put(key('a', id), name));
        restaurants.forEach((id, name) -> missing.put(key('r', id), name));
        Map<Long, String> a = new HashMap<>();
        Map<Long, String> r = new HashMap<>();
        translateAndSave(missing).forEach((k, name) ->
                (k.charAt(0) == 'a' ? a : r).put(Long.parseLong(k.substring(1)), name));
        return new Names(a, r);
    }

    /** 종류별 id → 한국어 이름 */
    public record Names(Map<Long, String> attractions, Map<Long, String> restaurants) {
    }

    /** 최근에 실패한 키는 빼고 AI에 묻고, 받은 이름을 저장한다. 받지 못한 키는 실패로 기록 */
    private Map<String, String> translateAndSave(Map<String, String> missing) {
        if (!props.isEnabled() || missing.isEmpty()) return Map.of();
        Instant now = clock.instant();
        Map<String, String> ask = new LinkedHashMap<>();
        missing.forEach((k, name) -> {
            Instant until = failedUntil.get(k);
            if ((until == null || !now.isBefore(until)) && name != null && !name.isBlank()) ask.put(k, name);
        });
        if (ask.isEmpty()) return Map.of();

        Map<String, String> made = translator.translate(ask);
        Instant retryAt = now.plusSeconds(props.getRetryAfterMinutes() * 60L);
        ask.keySet().stream().filter(k -> !made.containsKey(k)).forEach(k -> failedUntil.put(k, retryAt));
        made.keySet().forEach(failedUntil::remove);
        if (made.isEmpty()) return made;

        try {
            writeTransaction.executeWithoutResult(status -> made.forEach((k, name) -> {
                long id = Long.parseLong(k.substring(1));
                if (k.charAt(0) == 'a') attractionRepository.saveNameKo(id, name);
                else restaurantRepository.saveNameKo(id, name);
            }));
        } catch (RuntimeException e) {
            // 저장에 실패해도 이번 응답에는 이름을 쓴다 (다음에 다시 만든다)
            log.warn("[KoreanName] 저장 실패: {}", e.getMessage());
        }
        return made;
    }

    private static String key(char kind, Long id) {
        return kind + String.valueOf(id);
    }
}
