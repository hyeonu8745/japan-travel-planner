package com.example.japantravelplanner.dataimport;

import com.example.japantravelplanner.global.google.GooglePlacesClient;
import com.example.japantravelplanner.global.google.GooglePlacesClient.SearchPlace;
import com.example.japantravelplanner.global.util.CsvParser;
import com.example.japantravelplanner.restaurant.entity.GoogleMatchStatus;
import com.example.japantravelplanner.restaurant.match.RestaurantPlaceMatcher;
import com.example.japantravelplanner.restaurant.match.RestaurantPlaceMatcher.Criteria;
import com.example.japantravelplanner.restaurant.match.RestaurantPlaceMatcher.Decision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 새 관광지(OSM)의 Google 장소 ID 와 평점을 한 번 받아 둔다 (도시 확장 3단계 준비, 11-16 사용자 결정).
 * <ul>
 *   <li>장소 ID: Google 장소 ID 가 없는 관광지 전부 (Text Search Pro: 이름·좌표로 같은 곳인지 확인해야 해서 ID 만 받는 등급은 못 쓴다).
 *       상세 화면에서 Google 사진·영업시간을 받으려면 필요하다. ID 저장은 약관상 허용</li>
 *   <li>평점·리뷰 수: 꼭 가는 곳과 인기도 높은 곳(약 400곳)만 (Text Search Enterprise). 점수 계산에만 쓴다 (개인 프로젝트 범위)</li>
 *   <li>예전 도쿄 관광지와 같은 곳은 예전 CSV 의 평점을 그대로 옮긴다 (호출 없음)</li>
 * </ul>
 * 같은 곳 판단은 식당 매칭(RestaurantPlaceMatcher)을 거리 기준만 넓혀 쓴다. 한 곳씩 바로 저장해서 중간에 멈춰도 이어서 할 수 있다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceLookupJob {

    private final JdbcTemplate jdbc;
    private final GooglePlacesClient places;
    private final PlaceLookupProperties props;
    private final DataImportProperties importProps;

    /** 확인할 관광지 한 곳 */
    record Target(long id, String name, double lat, double lng, boolean wantsRating) {
    }

    /** 실행 결과 */
    public record Result(int copiedRatings, int checked, int matched, int closed, int notFound, int duplicates,
                         int ratings, int proCalls, int enterpriseCalls, boolean stoppedAtLimit) {
    }

    /** 평점 옮기기 → 장소 ID·평점 받기 (상한까지) */
    public Result run() {
        int copied = copyOldTokyoRatings();
        List<Target> targets = targets();
        log.info("[Places] 확인할 관광지 {}곳 (평점까지 {}곳). 예전 도쿄 평점 옮김 {}곳",
                targets.size(), targets.stream().filter(Target::wantsRating).count(), copied);

        Criteria criteria = new Criteria(props.getMaxDistanceMeters(), props.getMinNameSimilarity(),
                props.getLoanwordMaxDistanceMeters(), props.getLoanwordMinSimilarity());
        int checked = 0, matched = 0, closed = 0, notFound = 0, duplicates = 0, ratings = 0, pro = 0, ent = 0;
        boolean stopped = false;

        for (Target t : targets) {
            boolean withRating = t.wantsRating() && ent < props.getMaxEnterpriseCalls();
            if (!withRating && pro >= props.getMaxProCalls()) {
                stopped = true;
                break;
            }
            List<SearchPlace> found = withRating
                    ? places.searchTextWithRating(t.name(), t.lat(), t.lng(), props.getSearchRadiusMeters(), props.getPageSize())
                    : places.searchText(t.name(), t.lat(), t.lng(), props.getSearchRadiusMeters(), props.getPageSize());
            if (withRating) ent++; else pro++;

            Decision d = RestaurantPlaceMatcher.decide(t.name(), t.lat(), t.lng(), found, criteria);
            String placeId = d.placeId();
            GoogleMatchStatus status = d.status();
            if (placeId != null && placeIdTaken(placeId, t.id())) {
                duplicates++;
                log.info("[Places] {} → 이미 다른 관광지가 쓰는 장소 ID 라 저장하지 않음", t.name());
                placeId = null;
                status = GoogleMatchStatus.NOT_FOUND;
            }
            SearchPlace chosen = placeId == null ? null : pick(found, placeId);
            Double rating = withRating && chosen != null ? chosen.rating() : null;
            Integer reviews = withRating && chosen != null ? chosen.userRatingCount() : null;
            save(t.id(), placeId, status, rating, reviews);

            checked++;
            switch (status) {
                case MATCHED -> matched++;
                case CLOSED -> closed++;
                case NOT_FOUND -> notFound++;
            }
            if (rating != null) ratings++;
            if (checked % 100 == 0) {
                log.info("[Places] {}곳 확인 (Pro {}건 · Enterprise {}건)", checked, pro, ent);
            }
            pause();
        }
        return new Result(copied, checked, matched, closed, notFound, duplicates, ratings, pro, ent, stopped);
    }

    /** 예전 도쿄 관광지 CSV 의 평점·리뷰 수를 같은 장소 ID 를 가진 새 관광지에 옮긴다 (Google 호출 없음) */
    int copyOldTokyoRatings() {
        Path file = Path.of(importProps.getDataDir(), "attractions_tokyo.csv");
        if (!Files.exists(file)) return 0;
        int updated = 0;
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 1; i < lines.size(); i++) {
                if (lines.get(i).isBlank()) continue;
                List<String> r = CsvParser.parseLine(lines.get(i));
                if (r.get(6).isBlank() || r.get(7).isBlank()) continue;
                updated += jdbc.update("UPDATE public.attractions SET rating = ?, review_cnt = ?, "
                                + "google_match_status = 'MATCHED', google_checked_at = COALESCE(google_checked_at, now()) "
                                + "WHERE place_id = ? AND rating IS NULL",
                        Double.parseDouble(r.get(6)), (int) Double.parseDouble(r.get(7)), r.get(1));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return updated;
    }

    /** 장소 ID 가 없고 아직 확인하지 않은 관광지. 평점 받을 곳(꼭 가는 곳·인기도 높은 곳)을 먼저 */
    List<Target> targets() {
        return jdbc.query("SELECT id, name, ST_Y(location) AS lat, ST_X(location) AS lng, "
                        + "(COALESCE(must_see, false) OR COALESCE(popularity, 0) >= ?) AS wants_rating "
                        + "FROM public.attractions WHERE place_id IS NULL AND google_checked_at IS NULL "
                        + "AND location IS NOT NULL ORDER BY wants_rating DESC, popularity DESC NULLS LAST, id",
                (rs, n) -> new Target(rs.getLong("id"), rs.getString("name"), rs.getDouble("lat"),
                        rs.getDouble("lng"), rs.getBoolean("wants_rating")),
                props.getRatingMinPopularity());
    }

    /** 다른 관광지가 이미 이 장소 ID 를 쓰는지 (장소 ID 는 중복 금지) */
    private boolean placeIdTaken(String placeId, long selfId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM public.attractions WHERE place_id = ? AND id <> ?",
                Integer.class, placeId, selfId);
        return n != null && n > 0;
    }

    /** 확인 결과 저장 (평점은 받은 경우에만 덮어쓴다) */
    private void save(long id, String placeId, GoogleMatchStatus status, Double rating, Integer reviews) {
        jdbc.update("UPDATE public.attractions SET place_id = ?, google_match_status = ?, google_checked_at = ?, "
                        + "rating = COALESCE(?, rating), review_cnt = COALESCE(?, review_cnt) WHERE id = ?",
                placeId, status.name(), Timestamp.valueOf(LocalDateTime.now()), rating, reviews, id);
    }

    /** 검색 결과 중 고른 장소 */
    static SearchPlace pick(List<SearchPlace> found, String placeId) {
        return found.stream().filter(p -> placeId.equals(p.id())).findFirst().orElse(null);
    }

    private void pause() {
        if (props.getDelayMillis() <= 0) return;
        try {
            Thread.sleep(props.getDelayMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
