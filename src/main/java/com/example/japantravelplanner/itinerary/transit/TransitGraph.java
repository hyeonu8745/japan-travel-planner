package com.example.japantravelplanner.itinerary.transit;

import com.example.japantravelplanner.global.util.GeoUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 대중교통 노선 그래프 (11-22, 메모리). 노선마다 정차역 순서만 있고 시간표는 없다.
 *
 * OSM 은 같은 역이라도 노선마다 정차 지점(stop_position)이 따로 있어서, 이름이 같고 500m 안인 지점을 한 역(Station)으로 묶는다.
 * 그래야 "신주쿠"에서 다른 노선으로 갈아타는 것을 찾을 수 있다. 역 찾기는 0.01도 격자로 한다.
 */
public final class TransitGraph {

    static final double MERGE_METERS = 500;
    private static final double CELL = 0.01;   // 약 1km
    private static final Pattern STATION_SUFFIX = Pattern.compile("(駅|停留場|停留所|バス停|乗り場|のりば)$");
    private static final Pattern BRACKETS = Pattern.compile("[(（\\[［].*?[)）\\]］]");

    /** 역 (여러 정차 지점을 묶은 것) */
    public record Station(int index, String name, String nameEn, String nameKo, double lat, double lng) {
    }

    /**
     * 노선 하나 (한 방향)
     * @param stations 정차 순서 (Station 번호, 같은 역이 연달아 나오면 한 번)
     */
    public record Line(String id, String mode, String ref, String name, String operator, String colour, int[] stations) {
    }

    /** 이 역에 서는 노선과 그 노선에서의 순서 */
    public record Stop(int line, int position) {
    }

    /** DB·파일의 정차 지점 한 줄 */
    public record StopRow(String id, String name, String nameEn, String nameKo, double lat, double lng) {
    }

    /** DB·파일의 노선 한 줄 (stopIds: 정차 순서) */
    public record RouteRow(String id, String mode, String ref, String line, String operator, String colour,
                           List<String> stopIds) {
    }

    public static final TransitGraph EMPTY = new TransitGraph(List.of(), List.of());

    private final List<Station> stations = new ArrayList<>();
    private final List<Line> lines = new ArrayList<>();
    private final List<List<Stop>> stopsAt = new ArrayList<>();
    private final Map<Long, List<Integer>> grid = new HashMap<>();

    /** 정차 지점을 역으로 묶고 노선을 역 번호로 바꾼다 */
    public TransitGraph(List<StopRow> stopRows, List<RouteRow> routeRows) {
        Map<String, Integer> stationOf = new HashMap<>();
        Map<String, List<Integer>> byName = new HashMap<>();
        for (StopRow s : stopRows) {
            String key = key(s.name());
            Integer found = null;
            for (int idx : byName.getOrDefault(key, List.of())) {
                Station st = stations.get(idx);
                if (GeoUtils.distanceMeters(st.lat(), st.lng(), s.lat(), s.lng()) <= MERGE_METERS) {
                    found = idx;
                    break;
                }
            }
            if (found == null) {
                found = stations.size();
                stations.add(new Station(found, display(s.name()), s.nameEn(), s.nameKo(), s.lat(), s.lng()));
                stopsAt.add(new ArrayList<>());
                byName.computeIfAbsent(key, k -> new ArrayList<>()).add(found);
                grid.computeIfAbsent(cell(s.lat(), s.lng()), c -> new ArrayList<>()).add(found);
            } else if (stations.get(found).nameKo() == null && s.nameKo() != null) {
                Station st = stations.get(found);
                stations.set(found, new Station(found, st.name(), st.nameEn() == null ? s.nameEn() : st.nameEn(),
                        s.nameKo(), st.lat(), st.lng()));
            }
            stationOf.put(s.id(), found);
        }
        for (RouteRow r : routeRows) {
            List<Integer> seq = new ArrayList<>();
            for (String id : r.stopIds()) {
                Integer st = stationOf.get(id);
                if (st != null && (seq.isEmpty() || !seq.get(seq.size() - 1).equals(st))) seq.add(st);
            }
            if (seq.size() < 2) continue;
            int lineIndex = lines.size();
            int[] arr = seq.stream().mapToInt(Integer::intValue).toArray();
            lines.add(new Line(r.id(), r.mode(), joined(r.ref()), lineName(r.line(), r.operator()), joined(r.operator()),
                    colour(r.colour()), arr));
            for (int pos = 0; pos < arr.length; pos++) stopsAt.get(arr[pos]).add(new Stop(lineIndex, pos));
        }
    }

    /** 이름이 열차 종별뿐인 노선 ("快速急行", "各駅停車") */
    private static final java.util.regex.Pattern SERVICE_ONLY = java.util.regex.Pattern.compile(
            "^(快速急行|急行|準急|区間急行|区間準急|普通|快速|各駅停車|通勤急行|通勤準急|新快速|通勤快速|直通特急|特急)$");
    /** 회사 이름 → 짧은 이름 (종별뿐인 노선 이름 앞에 붙인다) */
    private static final Map<String, String> SHORT_OPERATOR = Map.ofEntries(
            Map.entry("近畿日本鉄道", "近鉄"), Map.entry("阪神電気鉄道", "阪神"), Map.entry("阪急電鉄", "阪急"),
            Map.entry("小田急電鉄", "小田急"), Map.entry("京王電鉄", "京王"), Map.entry("東急電鉄", "東急"),
            Map.entry("京阪電気鉄道", "京阪"), Map.entry("南海電気鉄道", "南海"), Map.entry("西日本鉄道", "西鉄"),
            Map.entry("山陽電気鉄道", "山陽"), Map.entry("西武鉄道", "西武"), Map.entry("東武鉄道", "東武"),
            Map.entry("京成電鉄", "京成"), Map.entry("京浜急行電鉄", "京急"), Map.entry("名古屋鉄道", "名鉄"));

    /** "快速急行" + "近畿日本鉄道;阪神電気鉄道" → "近鉄·阪神 快速急行". 그 밖에는 그대로 */
    static String lineName(String line, String operator) {
        if (line == null || operator == null || !SERVICE_ONLY.matcher(line.strip()).matches()) return line;
        List<String> names = new ArrayList<>();
        for (String op : operator.split(";")) {
            String o = op.strip();
            if (!o.isEmpty()) names.add(SHORT_OPERATOR.getOrDefault(o, o));
        }
        return names.isEmpty() ? line : String.join("·", names) + " " + line.strip();
    }

    /** OSM 의 여러 값 "K;B" → "K·B" */
    static String joined(String value) {
        if (value == null) return null;
        String v = String.join("·", java.util.Arrays.stream(value.split(";")).map(String::strip).filter(x -> !x.isEmpty()).toList());
        return v.isEmpty() ? null : v;
    }

    /** 색 이름(CSS)·3자리 색 → #RRGGBB. 모르는 값은 null */
    private static final Map<String, String> CSS_COLOURS = Map.ofEntries(
            Map.entry("blue", "#0000FF"), Map.entry("yellow", "#FFFF00"), Map.entry("gray", "#808080"),
            Map.entry("grey", "#808080"), Map.entry("orangered", "#FF4500"), Map.entry("deepskyblue", "#00BFFF"),
            Map.entry("green", "#008000"), Map.entry("darkblue", "#00008B"), Map.entry("red", "#FF0000"),
            Map.entry("darkgreen", "#006400"), Map.entry("orange", "#FFA500"), Map.entry("white", "#FFFFFF"),
            Map.entry("black", "#000000"), Map.entry("purple", "#800080"), Map.entry("brown", "#A52A2A"),
            Map.entry("pink", "#FFC0CB"), Map.entry("navy", "#000080"), Map.entry("skyblue", "#87CEEB"));

    static String colour(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String c = raw.strip();
        if (c.matches("#[0-9A-Fa-f]{6}")) return c.toUpperCase(java.util.Locale.ROOT);
        if (c.matches("#[0-9A-Fa-f]{3}")) {
            StringBuilder b = new StringBuilder("#");
            for (char ch : c.substring(1).toCharArray()) b.append(ch).append(ch);
            return b.toString().toUpperCase(java.util.Locale.ROOT);
        }
        return CSS_COLOURS.get(c.toLowerCase(java.util.Locale.ROOT));
    }

    /** 역 수 */
    public int stationCount() {
        return stations.size();
    }

    /** 노선 수 */
    public int lineCount() {
        return lines.size();
    }

    public Station station(int index) {
        return stations.get(index);
    }

    public Line line(int index) {
        return lines.get(index);
    }

    /** 이 역에 서는 노선들 */
    public List<Stop> stopsAt(int station) {
        return stopsAt.get(station);
    }

    /** 좌표에서 radius 미터 안의 역 (노선이 서는 역만, 가까운 순) */
    public List<int[]> near(double lat, double lng, double radiusMeters, int limit) {
        List<int[]> found = new ArrayList<>();   // [역 번호, 거리]
        int span = (int) Math.ceil(radiusMeters / 1000.0 / (CELL * 100)) + 1;
        long cx = Math.round(Math.floor(lat / CELL));
        long cy = Math.round(Math.floor(lng / CELL));
        for (long dx = -span; dx <= span; dx++) {
            for (long dy = -span; dy <= span; dy++) {
                for (int idx : grid.getOrDefault(pack(cx + dx, cy + dy), List.of())) {
                    if (stopsAt.get(idx).isEmpty()) continue;
                    Station st = stations.get(idx);
                    double d = GeoUtils.distanceMeters(lat, lng, st.lat(), st.lng());
                    if (d <= radiusMeters) found.add(new int[]{idx, (int) Math.round(d)});
                }
            }
        }
        found.sort((a, b) -> Integer.compare(a[1], b[1]));
        return found.size() > limit ? found.subList(0, limit) : found;
    }

    /** 노선에서 from ~ to 위치 사이 거리 (역 사이 직선거리 합) */
    public double rideMeters(Line line, int from, int to) {
        int a = Math.min(from, to), b = Math.max(from, to);
        double sum = 0;
        for (int i = a; i < b; i++) {
            Station s = stations.get(line.stations()[i]);
            Station t = stations.get(line.stations()[i + 1]);
            sum += GeoUtils.distanceMeters(s.lat(), s.lng(), t.lat(), t.lng());
        }
        return sum;
    }

    /** 같은 역인지 볼 이름: NFKC, 괄호·공백·「駅」 빼기 */
    static String key(String name) {
        String n = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFKC);
        n = BRACKETS.matcher(n).replaceAll("").replaceAll("\\s+", "");
        return STATION_SUFFIX.matcher(n).replaceAll("");
    }

    /** 화면 이름: 괄호 뒤 영어 등을 뗀 이름 */
    static String display(String name) {
        String n = BRACKETS.matcher(name == null ? "" : name).replaceAll("").strip();
        return n.isEmpty() ? name : n;
    }

    private static long cell(double lat, double lng) {
        return pack(Math.round(Math.floor(lat / CELL)), Math.round(Math.floor(lng / CELL)));
    }

    private static long pack(long x, long y) {
        return (x << 32) ^ (y & 0xffffffffL);
    }
}
