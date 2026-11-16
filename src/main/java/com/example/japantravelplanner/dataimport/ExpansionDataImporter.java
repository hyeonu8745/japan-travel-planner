package com.example.japantravelplanner.dataimport;

import com.example.japantravelplanner.attraction.entity.AttractionKind;
import com.example.japantravelplanner.attraction.policy.SensitivePlaces;
import com.example.japantravelplanner.dataimport.ExpansionRows.AttractionRow;
import com.example.japantravelplanner.dataimport.ExpansionRows.ChainRow;
import com.example.japantravelplanner.dataimport.ExpansionRows.FacilityRow;
import com.example.japantravelplanner.dataimport.ExpansionRows.TabelogRow;
import com.example.japantravelplanner.facility.entity.FacilityType;
import com.example.japantravelplanner.global.util.CsvParser;
import com.example.japantravelplanner.restaurant.entity.ChainCategory;
import com.example.japantravelplanner.restaurant.entity.RestaurantSource;
import com.example.japantravelplanner.trip.region.City;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 확장 데이터 가져오기 (도시 확장 2단계). 여행·일정·식당·관광지·편의 시설을 모두 비우고 정리된 파일로 다시 채운다.
 * 순서: 비우기 → 체인점 브랜드 → 식당(타베로그·캐글) → 체인점 지점 → 관광지 → 편의 시설
 * → 식당 백업 값 다시 붙이기(Google 매칭·한국어 이름) → 예전 도쿄 관광지 합치기(같은 곳은 Google 장소 ID 옮기기,
 * 새 데이터에 없는 유명한 곳은 더하기) → 관광지 백업 한국어 이름 → 식당 외래키 검사 켜기. 모두 한 트랜잭션이라 중간에 실패하면 아무것도 바뀌지 않는다.
 * 사용자 계정은 지우지 않는다. 여러 줄을 빨리 넣으려고 JPA 대신 JDBC 묶음 저장을 쓴다.
 */
@Service
@Slf4j
public class ExpansionDataImporter {

    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private static final String POINT = "ST_SetSRID(ST_MakePoint(?, ?), 4326)";

    static final String TABELOG_SQL = "INSERT INTO public.restaurants (store_id, name, nearest_station, nearest_distance, genre, "
                + "rating_val, review_cnt, budget_dinner, budget_lunch, budget_dinner_min, budget_dinner_max, "
                + "budget_lunch_min, budget_lunch_max, holiday, address, prefecture, municipalities_1, location, "
                + "city, source, business_hours, geo_level, location_suspect, closed_days, closed_irregular, "
                + "no_regular_holiday, closed_on_holidays, reservation_only, smoking_all_seats, korean_menu, "
                + "hyakumeiten, award_medal, standing_bar, created_at, updated_at) VALUES "
                + "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " + POINT + ", "
                + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    static final String CHAIN_SQL = "INSERT INTO public.restaurants (store_id, name, location, city, source, opening_hours, "
                + "brand_id, created_at, updated_at) VALUES (?, ?, " + POINT + ", ?, ?, ?, ?, ?, ?)";

    static final String ATTRACTION_SQL = "INSERT INTO public.attractions (osm_id, name, name_en, name_ko, kind, location, city, wikidata, "
                + "popularity, opening_hours, website, fee, history_note, night_view, must_see, "
                + "night_kind, night_hours, night_fee, night_closed, night_url, "
                + "official_status, official_reservation, official_closed_days, official_hours, official_fee, official_url, "
                + "photo_thumb, photo_width, photo_height, photo_credit, photo_license, photo_license_url, photo_artist, "
                + "photo_source, created_at, updated_at) VALUES (?, ?, ?, ?, ?, " + POINT + ", "
                + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    static final String OLD_TOKYO_SQL = "INSERT INTO public.attractions (place_id, name, address, ward, rating, "
            + "review_cnt, types, location, city, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, " + POINT
            + ", ?, ?, ?)";

    static final String FACILITY_SQL = "INSERT INTO public.facilities (osm_id, type, city, name, name_ja, name_en, name_ko, location, "
                + "operator, opening_hours, fee, wheelchair, brand, network, ref, level, capacity, parking, maxstay, "
                + "charge, bath_type) VALUES (?, ?, ?, ?, ?, ?, ?, " + POINT + ", ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final DataImportProperties props;

    /** 묶음 저장(JDBC)과 한 트랜잭션 */
    public ExpansionDataImporter(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                                 DataImportProperties props) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.props = props;
    }

    /** 넣은 건수 */
    public record Result(int brands, int restaurants, int chains, int attractions, int facilities,
                         int restaurantKeeps, int attractionPlaceIds, int oldTokyoAdded, int attractionKeeps) {
    }

    /** 예전 도쿄 관광지 한 줄 (data/processed/attractions_tokyo.csv, Google Places 로 모은 것) */
    record OldTokyoRow(String placeId, String name, String address, String ward, double lat, double lng,
                       Double rating, Integer reviewCnt, String types) {
    }

    /** 예전 도쿄 관광지 합치기 결과: Google 장소 ID 를 옮긴 새 관광지 수, 따로 더한 예전 관광지 수 */
    record OldTokyoMerge(int placeIdsMoved, int added) {
    }

    /** 전부 비우고 다시 넣는다 (한 트랜잭션) */
    public Result replaceAll() {
        Path dir = Path.of(props.getDataDir());
        List<TabelogRow> tabelog = read(dir.resolve("clean_restaurants_tabelog.json"), new TypeReference<>() { });
        List<ChainRow> chains = read(dir.resolve("clean_restaurants_chain.json"), new TypeReference<>() { });
        List<AttractionRow> attractions = read(dir.resolve("clean_attractions.json"), new TypeReference<>() { });
        List<FacilityRow> facilities = read(dir.resolve("clean_facilities.json"), new TypeReference<>() { });
        log.info("[Import] 파일 읽음: 식당 {} · 체인점 {} · 관광지 {} · 편의 시설 {}",
                tabelog.size(), chains.size(), attractions.size(), facilities.size());

        return tx.execute(status -> {
            clear();
            Map<String, Long> brandIds = insertBrands(chains);
            int restaurants = insertTabelog(tabelog);
            int chainCount = insertChains(chains, brandIds);
            int attractionCount = insertAttractions(attractions);
            int facilityCount = insertFacilities(facilities);
            int restaurantKeeps = reattachRestaurantKeeps();
            OldTokyoMerge merge = mergeOldTokyo(attractions);
            int attractionKeeps = reattachAttractionKeeps();
            validateForeignKeys();
            return new Result(brandIds.size(), restaurants, chainCount, attractionCount, facilityCount,
                    restaurantKeeps, merge.placeIdsMoved(), merge.added(), attractionKeeps);
        });
    }

    /** 여행과 거기 딸린 표(일정·대화·포스터 등), 식당·관광지·편의 시설·브랜드를 비운다. 사용자는 남긴다 */
    void clear() {
        jdbc.execute("TRUNCATE TABLE public.trips, public.restaurants, public.attractions, public.facilities, "
                + "public.chain_brands RESTART IDENTITY CASCADE");
        log.info("[Import] 여행·일정·식당·관광지·편의 시설을 비웠다");
    }

    /** 체인점 브랜드 (이름 → 새 id) */
    Map<String, Long> insertBrands(List<ChainRow> chains) {
        Map<String, ChainRow> byBrand = new LinkedHashMap<>();
        for (ChainRow c : chains) byBrand.putIfAbsent(c.brand(), c);
        List<Object[]> args = new ArrayList<>();
        for (ChainRow c : byBrand.values()) {
            args.add(new Object[]{c.brand(), c.brandKo(), chainCategory(c.category()).name(), c.genreKo(),
                    c.budgetMin(), c.budgetMax()});
        }
        jdbc.batchUpdate("INSERT INTO public.chain_brands (name, name_ko, category, genre_ko, budget_min, budget_max) "
                + "VALUES (?, ?, ?, ?, ?, ?)", args);
        Map<String, Long> ids = new LinkedHashMap<>();
        jdbc.query("SELECT id, name FROM public.chain_brands", rs -> {
            ids.put(rs.getString("name"), rs.getLong("id"));
        });
        return ids;
    }

    /** 타베로그·캐글 식당 */
    int insertTabelog(List<TabelogRow> rows) {
        return batch(TABELOG_SQL, rows, ExpansionDataImporter::tabelogArgs);
    }

    /** 체인점 지점 (가게 이름·좌표·영업시간, 나머지는 브랜드에) */
    int insertChains(List<ChainRow> rows, Map<String, Long> brandIds) {
        return batch(CHAIN_SQL, rows, c -> chainArgs(c, brandIds.get(c.brand())));
    }

    /** 관광지 */
    int insertAttractions(List<AttractionRow> rows) {
        return batch(ATTRACTION_SQL, rows, ExpansionDataImporter::attractionArgs);
    }

    /** 편의 시설 (종류를 모르는 줄은 건너뛴다) */
    int insertFacilities(List<FacilityRow> rows) {
        List<FacilityRow> known = rows.stream().filter(f -> FacilityType.ofCode(f.type()).isPresent()).toList();
        if (known.size() < rows.size()) {
            log.warn("[Import] 종류를 모르는 편의 시설 {}곳 건너뜀", rows.size() - known.size());
        }
        return batch(FACILITY_SQL, known, ExpansionDataImporter::facilityArgs);
    }

    /** 11-14 백업의 식당 Google 매칭 결과·한국어 이름을 store_id 로 다시 붙인다 */
    int reattachRestaurantKeeps() {
        Path file = Path.of(props.getKeepDir(), "restaurant_keep.csv");
        if (!Files.exists(file)) {
            log.warn("[Import] {} 가 없어 식당 백업 값을 건너뜀", file);
            return 0;
        }
        int updated = 0;
        for (String[] r : csvRows(file)) {
            updated += jdbc.update("UPDATE public.restaurants SET google_place_id = ?, google_match_status = ?, "
                            + "google_matched_at = ?, name_ko = COALESCE(name_ko, ?) WHERE store_id = ?",
                    blank(r[1]), blank(r[2]), timestamp(r[3]), blank(r[4]), r[0]);
        }
        return updated;
    }

    /**
     * 예전 도쿄 관광지(Google Places 1,434곳)를 새 관광지와 합친다 (11-16 사용자 결정 "유명한 곳만 합치기").
     * 같은 곳이면 Google 장소 ID 만 새 관광지에 옮기고, 새 데이터에 없는 곳은 리뷰가 많은 곳(기본 1,000개 이상)만
     * 예전 칸(평점·리뷰 수·types·구) 그대로 더한다. 추천하지 않는 장소(SensitivePlaces)는 더하지 않는다.
     * 아메요코·시부야 스크램블 교차로·쇼핑몰처럼 OSM 관광지 데이터에 없는 유명한 곳을 지키기 위해서다.
     */
    OldTokyoMerge mergeOldTokyo(List<AttractionRow> attractions) {
        Path file = Path.of(props.getDataDir(), "attractions_tokyo.csv");
        if (!Files.exists(file)) {
            log.warn("[Import] {} 가 없어 예전 도쿄 관광지 합치기를 건너뜀", file);
            return new OldTokyoMerge(0, 0);
        }
        List<OldTokyoRow> olds = csvRows(file).stream().map(ExpansionDataImporter::oldTokyoRow).toList();
        List<PlaceIdTransfer.NewPlace> news = attractions.stream()
                .filter(a -> "tokyo".equalsIgnoreCase(a.city()))
                .map(a -> new PlaceIdTransfer.NewPlace(a.osmId(), a.name(), a.nameEn(), a.lat(), a.lng()))
                .toList();
        Map<String, String> matched = PlaceIdTransfer.match(olds.stream()
                .map(o -> new PlaceIdTransfer.OldPlace(o.placeId(), o.name(), o.lat(), o.lng())).toList(), news);
        jdbc.batchUpdate("UPDATE public.attractions SET place_id = ? WHERE osm_id = ?",
                matched.entrySet().stream().map(e -> new Object[]{e.getValue(), e.getKey()}).toList());

        List<OldTokyoRow> famous = famousUnmatched(olds, new HashSet<>(matched.values()),
                props.getMinOldTokyoReviews());
        int added = batch(OLD_TOKYO_SQL, famous, ExpansionDataImporter::oldTokyoArgs);
        log.info("[Import] 예전 도쿄 관광지 {}곳: {}곳은 새 관광지에 Google 장소 ID 를 옮기고, 새 데이터에 없는 유명한 곳 {}곳을 더함",
                olds.size(), matched.size(), added);
        return new OldTokyoMerge(matched.size(), added);
    }

    /** 새 데이터와 짝이 없고, 리뷰가 많고, 추천하지 않는 장소가 아닌 예전 관광지 */
    static List<OldTokyoRow> famousUnmatched(List<OldTokyoRow> olds, Set<String> matchedPlaceIds,
                                             int minReviews) {
        return olds.stream()
                .filter(o -> !matchedPlaceIds.contains(o.placeId()))
                .filter(o -> o.reviewCnt() != null && o.reviewCnt() >= minReviews)
                .filter(o -> !SensitivePlaces.isExcluded(o.name()))
                .toList();
    }

    /** attractions_tokyo.csv 한 줄 (ward, place_id, name, address, latitude, longitude, rating, review_cnt, types) */
    static OldTokyoRow oldTokyoRow(String[] r) {
        return new OldTokyoRow(r[1], r[2], blank(r[3]), blank(r[0]), Double.parseDouble(r[4]), Double.parseDouble(r[5]),
                r[6].isBlank() ? null : Double.parseDouble(r[6]),
                r[7].isBlank() ? null : (int) Double.parseDouble(r[7]), blank(r[8]));
    }

    /** 예전 도쿄 관광지 한 줄의 값 (INSERT 칸 순서대로) */
    static Object[] oldTokyoArgs(OldTokyoRow o) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        return new Object[]{o.placeId(), o.name(), o.address(), o.ward(), o.rating(), o.reviewCnt(), o.types(),
                o.lng(), o.lat(), City.TOKYO.name(), now, now};
    }

    /** 11-14 백업의 관광지 한국어 이름을 Google 장소 ID 로 붙인다 (새 데이터에 한국어 이름이 없을 때만) */
    int reattachAttractionKeeps() {
        Path file = Path.of(props.getKeepDir(), "attraction_keep.csv");
        if (!Files.exists(file)) {
            log.warn("[Import] {} 가 없어 관광지 백업 값을 건너뜀", file);
            return 0;
        }
        int updated = 0;
        for (String[] r : csvRows(file)) {
            updated += jdbc.update("UPDATE public.attractions SET name_ko = ? WHERE place_id = ? AND name_ko IS NULL",
                    blank(r[1]), r[0]);
        }
        return updated;
    }

    /** V5 에서 NOT VALID 로 건 식당 외래키를 검사한다 (비운 뒤라 통과해야 한다) */
    void validateForeignKeys() {
        jdbc.execute("ALTER TABLE public.trip_excluded_restaurants VALIDATE CONSTRAINT fk_trip_excluded_restaurant");
        jdbc.execute("ALTER TABLE public.itinerary_stop_alternatives VALIDATE CONSTRAINT fk_stop_alternative_restaurant");
    }

    // ---- 한 줄 → SQL 값 ----

    /** 타베로그 한 줄의 값 (INSERT 칸 순서대로) */
    static Object[] tabelogArgs(TabelogRow r) {
        AddressParts parts = AddressParts.of(r.address());
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        return new Object[]{r.storeId(), r.name(), r.nearestStation(), r.nearestDistance(), joinGenres(r.genres()),
                r.rating(), r.reviewCount(), r.budgetDinner(), r.budgetLunch(), r.budgetDinnerMin(),
                r.budgetDinnerMax(), r.budgetLunchMin(), r.budgetLunchMax(), r.holiday(), r.address(),
                parts.prefecture(), parts.municipality(), r.lng(), r.lat(), city(r.city()).name(),
                source(r.source()).name(), r.businessHours(), r.geoLevel(), r.suspect(), joinDays(r.closedDays()),
                r.closedIrregular(), r.noRegularHoliday(), r.closedOnHolidays(), r.reservationOnly(),
                r.smokingAllSeats(), r.koreanMenu(), r.hyakumeiten(), r.awardMedal(), r.standingBar(), now, now};
    }

    /** 관광지 한 줄의 값 (INSERT 칸 순서대로) */
    static Object[] attractionArgs(AttractionRow a) {
        var n = a.nightInfo();
        var o = a.officialInfo();
        var p = a.photo();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        return new Object[]{a.osmId(), a.name(), cut(a.nameEn(), 200), cut(a.nameKo(), 200),
                AttractionKind.valueOf(a.kind()).name(), a.lng(), a.lat(), city(a.city()).name(), a.wikidata(),
                a.popularity(), cut(a.openingHours(), 300), cut(a.website(), 300), cut(a.fee(), 30),
                cut(a.historyNote(), 300), a.nightView(), a.mustSee(),
                n == null ? null : n.kind(), n == null ? null : n.hours(), n == null ? null : n.fee(),
                n == null ? null : n.closed(), n == null ? null : n.url(),
                o == null ? null : o.status(), o == null ? null : o.reservation(), o == null ? null : o.closedDays(),
                o == null ? null : o.hours(), o == null ? null : o.fee(), o == null ? null : o.url(),
                p == null ? null : p.thumb(), p == null ? null : p.width(), p == null ? null : p.height(),
                p == null ? null : p.credit(), p == null ? null : p.license(), p == null ? null : p.licenseUrl(),
                p == null ? null : p.artist(), p == null ? null : p.source(), now, now};
    }

    /** 체인점 지점 한 줄의 값 (INSERT 칸 순서대로) */
    static Object[] chainArgs(ChainRow c, Long brandId) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        return new Object[]{c.osmId(), c.name(), c.lng(), c.lat(), city(c.city()).name(),
                RestaurantSource.CHAIN.name(), c.openingHours(), brandId, now, now};
    }

    /** 편의 시설 한 줄의 값 (INSERT 칸 순서대로) */
    static Object[] facilityArgs(FacilityRow f) {
        return new Object[]{f.osmId(), FacilityType.ofCode(f.type()).orElseThrow().name(),
                city(f.city()).name(), cut(f.name(), 100), cut(f.nameJa(), 100), cut(f.nameEn(), 100),
                cut(f.nameKo(), 100), f.lng(), f.lat(), cut(f.operator(), 100), cut(f.openingHours(), 200),
                cut(f.fee(), 100), f.wheelchair(), cut(f.brand(), 60), cut(f.network(), 40), cut(f.ref(), 60),
                cut(f.level(), 60), cut(f.capacity(), 20), f.parking(), cut(f.maxstay(), 40), cut(f.charge(), 200),
                f.bathType()};
    }

    /** 데이터 파일의 도시 이름(tokyo) → City. 모르는 도시면 가져오기를 멈춘다 */
    static City city(String code) {
        return City.of(code).orElseThrow(() -> new IllegalStateException("모르는 도시: " + code));
    }

    /** 데이터 파일의 출처(kaggle/apify) → 식당 출처 */
    static RestaurantSource source(String code) {
        return switch (code == null ? "" : code.toLowerCase(Locale.ROOT)) {
            case "kaggle" -> RestaurantSource.KAGGLE;
            case "apify" -> RestaurantSource.TABELOG;
            default -> throw new IllegalStateException("모르는 식당 출처: " + code);
        };
    }

    /** 체인점 종류 글자 → ChainCategory */
    static ChainCategory chainCategory(String code) {
        return ChainCategory.valueOf(code.toUpperCase(Locale.ROOT));
    }

    /** 장르 목록 → 쉼표 글자 (예전 도쿄 데이터와 같은 모양: 焼鳥,居酒屋) */
    static String joinGenres(List<String> genres) {
        return genres == null || genres.isEmpty() ? null : String.join(",", genres);
    }

    /** 정기 휴일 요일 목록 → 쉼표 글자 (TUE,WED). 없으면 null */
    static String joinDays(List<String> days) {
        return days == null || days.isEmpty() ? null : String.join(",", days);
    }

    /** 칸 길이를 넘는 글자는 자른다 */
    static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static Timestamp timestamp(String s) {
        return s == null || s.isBlank() ? null : Timestamp.valueOf(s.strip());
    }

    private <T> int batch(String sql, List<T> rows, Function<T, Object[]> args) {
        int done = 0;
        for (int from = 0; from < rows.size(); from += props.getBatchSize()) {
            List<T> part = rows.subList(from, Math.min(rows.size(), from + props.getBatchSize()));
            jdbc.batchUpdate(sql, part.stream().map(args).toList());
            done += part.size();
        }
        return done;
    }

    private static <T> List<T> read(Path file, TypeReference<List<T>> type) {
        try {
            return JSON.readValue(Files.readString(file, StandardCharsets.UTF_8), type);
        } catch (IOException e) {
            throw new UncheckedIOException("데이터 파일을 읽지 못함: " + file, e);
        }
    }

    /** CSV 줄들 (첫 줄 제목은 뺀다, BOM 제거) */
    private static List<String[]> csvRows(Path file) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<String[]> rows = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isBlank()) continue;
                rows.add(CsvParser.parseLine(line).toArray(String[]::new));
            }
            return rows;
        } catch (IOException e) {
            throw new UncheckedIOException("CSV 를 읽지 못함: " + file, e);
        }
    }
}
