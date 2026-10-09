package com.example.japantravelplanner.restaurant.match;

import com.example.japantravelplanner.global.google.GooglePlacesClient.SearchPlace;
import com.example.japantravelplanner.global.util.GeoUtils;
import com.example.japantravelplanner.restaurant.entity.GoogleMatchStatus;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Google 검색 결과 중 타베로그 식당과 같은 가게를 고른다 (계산만, 외부 호출 없음).
 *
 * 같은 가게로 보는 조건 (10-09 표본 50곳으로 정함):
 *  - 우리 좌표와 maxDistance 안 (도쿄역 지하상가처럼 좌표가 수백 m 어긋나는 곳이 있어서 넉넉하게)
 *  - 이름 비슷한 정도 minSimilarity 이상 (한쪽 이름이 다른 쪽에 들어 있으면 1.0: 「うりんぼ」↔「博多ラーメン うりんぼ」)
 *  - 지점 이름이 서로 다르지 않음 (「クラウゾ 本店」↔「クラウゾ 2号店」은 88m 거리에 이름도 맞지만 다른 가게였다)
 * 이름이 전혀 다른 가까운 가게는 받지 않는다. 폐업한 자리에 들어선 다른 가게가 0~8m 거리에서 나왔기 때문이다
 * (그 대신 「ザ グレートバーガー」↔「THE GREAT BURGER」 같은 영어 이름 가게는 놓친다).
 */
public final class RestaurantPlaceMatcher {

    static final String CLOSED_PERMANENTLY = "CLOSED_PERMANENTLY";

    private static final Pattern SYMBOLS =
            Pattern.compile("[\\s\\u3000・·\\-‐－_/()（）\\[\\]【】「」『』'\".,、。!！?？&＆]");
    private static final Pattern TRAILING_BRANCH = Pattern.compile("(本店|支店|店)$");

    private RestaurantPlaceMatcher() {
    }

    /** @param placeId MATCHED·CLOSED일 때 찾은 장소, NOT_FOUND면 null */
    public record Decision(GoogleMatchStatus status, String placeId) {

        static Decision notFound() {
            return new Decision(GoogleMatchStatus.NOT_FOUND, null);
        }
    }

    public static Decision decide(String name, double lat, double lng, List<SearchPlace> places,
                                  double maxDistanceMeters, double minNameSimilarity) {
        SearchPlace best = null;
        double bestSimilarity = -1;
        double bestDistance = Double.MAX_VALUE;

        for (SearchPlace place : places) {
            if (place == null || place.id() == null || place.nameText() == null || place.location() == null
                    || place.location().latitude() == null || place.location().longitude() == null) {
                continue;
            }
            double distance = GeoUtils.distanceMeters(lat, lng,
                    place.location().latitude(), place.location().longitude());
            double similarity = nameSimilarity(name, place.nameText());
            if (distance > maxDistanceMeters || similarity < minNameSimilarity
                    || branchConflict(name, place.nameText())) {
                continue;
            }
            // 이름이 더 비슷한 곳, 같으면 더 가까운 곳 (검색 순위보다 우선: 3순위가 맞는 가게인 경우가 있었다)
            if (similarity > bestSimilarity || (similarity == bestSimilarity && distance < bestDistance)) {
                best = place;
                bestSimilarity = similarity;
                bestDistance = distance;
            }
        }

        if (best == null) return Decision.notFound();
        if (CLOSED_PERMANENTLY.equals(best.businessStatus())) {
            return new Decision(GoogleMatchStatus.CLOSED, best.id());
        }
        return new Decision(GoogleMatchStatus.MATCHED, best.id());
    }

    /** 전각·반각, 대소문자, 띄어쓰기·기호, 끝의 「店」「本店」「支店」을 무시한 이름 */
    static String normalize(String name) {
        if (name == null) return "";
        String s = Normalizer.normalize(name, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        s = SYMBOLS.matcher(s).replaceAll("");
        return TRAILING_BRANCH.matcher(s).replaceAll("");
    }

    /**
     * 이름 비슷한 정도 0~1. 한쪽이 다른 쪽에 통째로 들어 있으면 1.0,
     * 아니면 두 글자씩 끊은 조각이 얼마나 겹치는지 (Dice 계수)
     */
    static double nameSimilarity(String a, String b) {
        String x = normalize(a);
        String y = normalize(b);
        if (x.isEmpty() || y.isEmpty()) return 0;
        if (x.contains(y) || y.contains(x)) return 1.0;

        Set<String> gx = bigrams(x);
        Set<String> gy = bigrams(y);
        Set<String> common = new HashSet<>(gx);
        common.retainAll(gy);
        return 2.0 * common.size() / (gx.size() + gy.size());
    }

    private static Set<String> bigrams(String s) {
        Set<String> result = new HashSet<>();
        if (s.length() < 2) {
            result.add(s);
            return result;
        }
        for (int i = 0; i < s.length() - 1; i++) {
            result.add(s.substring(i, i + 2));
        }
        return result;
    }

    /**
     * 띄어쓰기로 구분된 마지막 말이 「…店」이면 그 지점 이름 (「本店」→"本", 「2号店」→"2号", 「グランスタ東京店」→"グランスタ東京").
     * 한 덩어리 이름(「揚州飯店」「バンコクスパイス新宿」)은 지점을 나눌 수 없어서 null
     */
    static String branchOf(String name) {
        if (name == null) return null;
        String[] tokens = Normalizer.normalize(name, Normalizer.Form.NFKC).trim().split("\\s+");
        if (tokens.length < 2) return null;
        String last = tokens[tokens.length - 1];
        if (!last.endsWith("店") || last.length() < 2) return null;
        return last.substring(0, last.length() - 1).toLowerCase(Locale.ROOT);
    }

    /** 둘 다 지점 이름이 있는데 서로 다르면 다른 가게 (「グランスタ」↔「グランスタ東京」처럼 한쪽이 포함되면 같은 곳으로 본다) */
    static boolean branchConflict(String a, String b) {
        String x = branchOf(a);
        String y = branchOf(b);
        if (x == null || y == null) return false;
        return !x.contains(y) && !y.contains(x);
    }
}
