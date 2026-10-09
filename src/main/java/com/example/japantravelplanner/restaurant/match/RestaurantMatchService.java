package com.example.japantravelplanner.restaurant.match;

import com.example.japantravelplanner.global.google.GooglePlacesClient;
import com.example.japantravelplanner.global.google.GooglePlacesClient.SearchPlace;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.restaurant.match.RestaurantPlaceMatcher.Decision;
import com.example.japantravelplanner.restaurant.repository.RestaurantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 타베로그 식당을 Google 장소와 맞춰보고 결과(place ID, 상태)를 저장한다.
 *
 * 방식 (10-09 결정): 일정을 만들 때 고른 식당 중 아직 확인 안 한 곳만 확인한다.
 * 9,399곳을 미리 전부 확인하는 일괄 매칭은 나중에 같은 verify()를 배치로 돌려서 확장할 수 있다.
 *
 * - Google 호출은 트랜잭션 밖에서, 식당끼리 동시에 (일정 하나에 끼니 10곳 안팎)
 * - Google이 실패한 식당은 기록하지 않고 통과시킨다 (다음에 다시 확인. 설계 원칙 "불확실하면 거르지 않는다")
 * - 저장하는 것은 place ID와 우리가 판정한 결과뿐 (Google 이름·좌표는 비교에만 쓰고 버린다)
 */
@Slf4j
@Service
public class RestaurantMatchService {

    private final RestaurantRepository restaurantRepository;
    private final GooglePlacesClient placesClient;
    private final RestaurantMatchProperties props;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;

    public RestaurantMatchService(RestaurantRepository restaurantRepository,
                                  GooglePlacesClient placesClient,
                                  RestaurantMatchProperties props,
                                  PlatformTransactionManager transactionManager) {
        this.restaurantRepository = restaurantRepository;
        this.placesClient = placesClient;
        this.props = props;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    /** 일정 생성에서 "확인 → 다시 계산"을 몇 번까지 할지. 꺼져 있으면 0 */
    public int verifyRounds() {
        return props.isEnabled() ? props.getVerifyRounds() : 0;
    }

    /**
     * 아직 확인하지 않은 식당을 Google에서 찾아보고 결과를 저장한다. 트랜잭션 안에서 부르지 말 것 (외부 호출)
     *
     * @return 이번 확인으로 새로 끼니 후보에서 빠지게 된 식당 수 (찾지 못함·폐업)
     */
    public int verify(Collection<Long> restaurantIds) {
        if (!props.isEnabled() || restaurantIds == null || restaurantIds.isEmpty()) return 0;

        List<Target> targets = readTransaction.execute(status ->
                restaurantRepository.findAllById(Set.copyOf(restaurantIds)).stream()
                        .filter(r -> r.getGoogleMatchStatus() == null && r.getLocation() != null)
                        .map(Target::of)
                        .toList());
        if (targets == null || targets.isEmpty()) return 0;

        Map<Long, Decision> decisions = search(targets);
        if (decisions.isEmpty()) return 0;

        LocalDateTime now = LocalDateTime.now();
        writeTransaction.executeWithoutResult(status -> {
            for (Restaurant r : restaurantRepository.findAllById(decisions.keySet())) {
                if (r.getGoogleMatchStatus() != null) continue;   // 그사이 다른 요청이 먼저 확인했다
                Decision decision = decisions.get(r.getId());
                r.recordGoogleMatch(decision.status(), decision.placeId(), now);
            }
        });

        int excluded = (int) decisions.values().stream().filter(d -> d.status().excludesFromMeals()).count();
        log.info("[Match] 식당 {}곳 확인 → 결과 {}곳 저장, 후보에서 빠짐 {}곳", targets.size(), decisions.size(), excluded);
        return excluded;
    }

    private Map<Long, Decision> search(List<Target> targets) {
        Map<Long, Decision> result = new ConcurrentHashMap<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Target target : targets) {
                executor.submit(() -> {
                    try {
                        List<SearchPlace> places = placesClient.searchText(target.query(), target.lat(), target.lng(),
                                props.getSearchRadiusMeters(), props.getPageSize());
                        Decision decision = RestaurantPlaceMatcher.decide(target.name(), target.lat(), target.lng(),
                                places, props.getMaxDistanceMeters(), props.getMinNameSimilarity());
                        result.put(target.id(), decision);
                        log.info("[Match] 식당 id={} {} → {}", target.id(), target.name(), decision.status());
                    } catch (RuntimeException e) {
                        log.warn("[Match] 식당 id={} 확인 실패, 이번에는 통과: {}", target.id(), e.getMessage());
                    }
                });
            }
        }   // close()가 모든 작업이 끝날 때까지 기다린다
        return result;
    }

    /** 트랜잭션 밖으로 가지고 나갈 식당 정보 */
    record Target(Long id, String name, String query, double lat, double lng) {

        static Target of(Restaurant r) {
            String query = r.getAddress() == null ? r.getName() : r.getName() + " " + r.getAddress();
            return new Target(r.getId(), r.getName(), query, r.getLocation().getY(), r.getLocation().getX());
        }
    }
}
