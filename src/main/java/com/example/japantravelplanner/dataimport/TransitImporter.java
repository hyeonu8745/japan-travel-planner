package com.example.japantravelplanner.dataimport;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 대중교통 노선 가져오기 (11-22). data/processed/clean_transit.json (data/scripts/transit_build.py 가 만든다) →
 * transit_stops·transit_routes 를 통째로 바꾼다 (한 트랜잭션). 다른 데이터(여행·식당·관광지)는 건드리지 않는다.
 */
@Service
@Slf4j
public class TransitImporter {

    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private static final int BATCH = 1000;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final DataImportProperties props;

    /** 통째로 바꾸기라 한 트랜잭션으로 */
    public TransitImporter(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, DataImportProperties props) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.props = props;
    }

    /** 파일의 한 역 */
    record StopRow(String name, String nameEn, String nameKo, double lat, double lng) {
    }

    /** 파일의 한 노선 (stops: 정차 순서) */
    record RouteRow(String id, String mode, String ref, String line, String name, String nameEn, String operator,
                    String colour, List<String> stops) {
    }

    record TransitFile(Map<String, StopRow> stops, List<RouteRow> routes) {
    }

    /** @param stops 넣은 역 수, routes 넣은 노선 수 */
    public record Result(int stops, int routes) {
    }

    /** 파일을 읽어 두 표를 비우고 다시 넣는다 */
    public Result replaceAll() {
        TransitFile file = read(Path.of(props.getDataDir(), "clean_transit.json"));
        return transaction.execute(status -> {
            jdbc.update("DELETE FROM public.transit_routes");
            jdbc.update("DELETE FROM public.transit_stops");
            List<Object[]> stops = new ArrayList<>();
            file.stops().forEach((id, s) -> stops.add(stopArgs(id, s)));
            batch("INSERT INTO public.transit_stops (id, name, name_en, name_ko, lat, lng) VALUES (?, ?, ?, ?, ?, ?)", stops);
            List<Object[]> routes = file.routes().stream().map(TransitImporter::routeArgs).toList();
            batch("INSERT INTO public.transit_routes (id, mode, ref, line, name, name_en, operator, colour, stop_ids) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", routes);
            return new Result(stops.size(), routes.size());
        });
    }

    static Object[] stopArgs(String id, StopRow s) {
        return new Object[]{id, cut(s.name(), 100), cut(s.nameEn(), 100), cut(s.nameKo(), 100), s.lat(), s.lng()};
    }

    static Object[] routeArgs(RouteRow r) {
        return new Object[]{cut(r.id(), 120), r.mode(), cut(r.ref(), 40), cut(r.line(), 200), cut(r.name(), 200),
                cut(r.nameEn(), 200), cut(r.operator(), 120), cut(r.colour(), 20), String.join(",", r.stops())};
    }

    private void batch(String sql, List<Object[]> rows) {
        for (int i = 0; i < rows.size(); i += BATCH) {
            jdbc.batchUpdate(sql, rows.subList(i, Math.min(rows.size(), i + BATCH)));
        }
    }

    static TransitFile read(Path file) {
        try {
            return JSON.readValue(Files.readString(file, StandardCharsets.UTF_8), TransitFile.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String cut(String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max);
    }
}
