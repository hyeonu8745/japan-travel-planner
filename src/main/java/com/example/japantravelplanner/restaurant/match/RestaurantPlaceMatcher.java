package com.example.japantravelplanner.restaurant.match;

import com.example.japantravelplanner.global.google.GooglePlacesClient.SearchPlace;
import com.example.japantravelplanner.global.util.GeoUtils;
import com.example.japantravelplanner.restaurant.entity.GoogleMatchStatus;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Google 검색 결과 중 타베로그 식당과 같은 가게를 고른다 (계산만, 외부 호출 없음).
 *
 * 같은 가게로 보는 조건 (10-09 표본 50곳으로 정함):
 *  - 우리 좌표와 maxDistance 안 (도쿄역 지하상가처럼 좌표가 수백 m 어긋나는 곳이 있어서 넉넉하게)
 *  - 이름 비슷한 정도 minSimilarity 이상 (한쪽 이름이 다른 쪽에 들어 있으면 1.0: 「うりんぼ」↔「博多ラーメン うりんぼ」)
 *  - 지점 이름이 서로 다르지 않음 (「クラウゾ 本店」↔「クラウゾ 2号店」은 88m 거리에 이름도 맞지만 다른 가게였다)
 * 이름이 전혀 다른 가까운 가게는 받지 않는다. 폐업한 자리에 들어선 다른 가게가 0~8m 거리에서 나왔기 때문이다.
 *
 * 외래어 이름: 우리 이름이 가타카나이고 Google 이름이 영어면 글자 비교로는 0이 나온다
 * (「ボンダイ コーヒー サンドウィッチーズ」↔「Bondi Coffee Sandwiches」가 이 때문에 빠졌다).
 * 그래서 가타카나를 로마자로 바꾼 뒤 자음 뼈대끼리 비교한다 (「bndkhsndwCs」↔「bndkfsndwCs」).
 * 이 비교는 더 가까운 거리(loanwordMaxDistance)에서만 받는다.
 * 표기가 많이 다른 가게(매우 짧은 이름 등)는 여전히 놓칠 수 있다.
 */
public final class RestaurantPlaceMatcher {

    static final String CLOSED_PERMANENTLY = "CLOSED_PERMANENTLY";

    private static final Pattern SYMBOLS =
            Pattern.compile("[\\s\\u3000・·\\-‐－_/()（）\\[\\]【】「」『』'\".,、。!！?？&＆]");
    private static final Pattern TRAILING_BRANCH = Pattern.compile("(本店|支店|店)$");

    private RestaurantPlaceMatcher() {
    }

    /**
     * 같은 가게로 보는 기준
     * @param maxDistanceMeters         이름이 맞을 때 허용 거리
     * @param minNameSimilarity         이름 비슷한 정도 기준
     * @param loanwordMaxDistanceMeters 가타카나 ↔ 영어 비교로 맞출 때 허용 거리 (더 엄격하게)
     * @param loanwordMinSimilarity     가타카나 ↔ 영어 자음 뼈대 비슷한 정도 기준
     */
    public record Criteria(double maxDistanceMeters, double minNameSimilarity,
                           double loanwordMaxDistanceMeters, double loanwordMinSimilarity) {
    }

    /** @param placeId MATCHED·CLOSED일 때 찾은 장소, NOT_FOUND면 null */
    public record Decision(GoogleMatchStatus status, String placeId) {

        static Decision notFound() {
            return new Decision(GoogleMatchStatus.NOT_FOUND, null);
        }
    }

    public static Decision decide(String name, double lat, double lng, List<SearchPlace> places, Criteria criteria) {
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
            if (branchConflict(name, place.nameText())) continue;

            double similarity = nameSimilarity(name, place.nameText());
            boolean accepted = distance <= criteria.maxDistanceMeters() && similarity >= criteria.minNameSimilarity();
            if (!accepted && distance <= criteria.loanwordMaxDistanceMeters()) {
                double loanword = loanwordSimilarity(name, place.nameText());
                if (loanword >= criteria.loanwordMinSimilarity()) {
                    similarity = loanword;
                    accepted = true;
                }
            }
            if (!accepted) continue;

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

    // ===== 가타카나 ↔ 영어 이름 =====

    private static final Map<Character, String> KANA = Map.ofEntries(
            Map.entry('ア', "a"), Map.entry('イ', "i"), Map.entry('ウ', "u"), Map.entry('エ', "e"), Map.entry('オ', "o"),
            Map.entry('カ', "ka"), Map.entry('キ', "ki"), Map.entry('ク', "ku"), Map.entry('ケ', "ke"), Map.entry('コ', "ko"),
            Map.entry('サ', "sa"), Map.entry('シ', "shi"), Map.entry('ス', "su"), Map.entry('セ', "se"), Map.entry('ソ', "so"),
            Map.entry('タ', "ta"), Map.entry('チ', "chi"), Map.entry('ツ', "tsu"), Map.entry('テ', "te"), Map.entry('ト', "to"),
            Map.entry('ナ', "na"), Map.entry('ニ', "ni"), Map.entry('ヌ', "nu"), Map.entry('ネ', "ne"), Map.entry('ノ', "no"),
            Map.entry('ハ', "ha"), Map.entry('ヒ', "hi"), Map.entry('フ', "fu"), Map.entry('ヘ', "he"), Map.entry('ホ', "ho"),
            Map.entry('マ', "ma"), Map.entry('ミ', "mi"), Map.entry('ム', "mu"), Map.entry('メ', "me"), Map.entry('モ', "mo"),
            Map.entry('ヤ', "ya"), Map.entry('ユ', "yu"), Map.entry('ヨ', "yo"),
            Map.entry('ラ', "ra"), Map.entry('リ', "ri"), Map.entry('ル', "ru"), Map.entry('レ', "re"), Map.entry('ロ', "ro"),
            Map.entry('ワ', "wa"), Map.entry('ヲ', "o"), Map.entry('ン', "n"),
            Map.entry('ガ', "ga"), Map.entry('ギ', "gi"), Map.entry('グ', "gu"), Map.entry('ゲ', "ge"), Map.entry('ゴ', "go"),
            Map.entry('ザ', "za"), Map.entry('ジ', "ji"), Map.entry('ズ', "zu"), Map.entry('ゼ', "ze"), Map.entry('ゾ', "zo"),
            Map.entry('ダ', "da"), Map.entry('ヂ', "ji"), Map.entry('ヅ', "zu"), Map.entry('デ', "de"), Map.entry('ド', "do"),
            Map.entry('バ', "ba"), Map.entry('ビ', "bi"), Map.entry('ブ', "bu"), Map.entry('ベ', "be"), Map.entry('ボ', "bo"),
            Map.entry('パ', "pa"), Map.entry('ピ', "pi"), Map.entry('プ', "pu"), Map.entry('ペ', "pe"), Map.entry('ポ', "po"),
            Map.entry('ヴ', "vu"),
            Map.entry('ァ', "a"), Map.entry('ィ', "i"), Map.entry('ゥ', "u"), Map.entry('ェ', "e"), Map.entry('ォ', "o"),
            Map.entry('ャ', "ya"), Map.entry('ュ', "yu"), Map.entry('ョ', "yo"));

    private static final String SMALL_VOWELS = "ァィゥェォ";

    /**
     * 가타카나 → 로마자 (자음 뼈대만 비교하므로 모음·장음 표기는 대충이어도 된다).
     * 「ッ」「ー」는 버리고, 「ウィ」「ウェ」처럼 ウ 뒤에 작은 모음이 오면 w. 가타카나가 아닌 글자는 그대로 둔다
     */
    static String romanize(String text) {
        String s = Normalizer.normalize(text, Normalizer.Form.NFKC);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 'ッ' || c == 'ー') continue;
            if (c == 'ウ' && i + 1 < s.length() && SMALL_VOWELS.indexOf(s.charAt(i + 1)) >= 0) {
                out.append('w');
                continue;
            }
            String roman = KANA.get(c);
            out.append(roman == null ? String.valueOf(c) : roman);
        }
        return out.toString();
    }

    /** 소리가 비슷한 철자를 하나로 모은 뒤 모음을 빼고, 같은 글자가 이어지면 하나로 (coffee → kf, koohii → kh) */
    static String skeleton(String latin) {
        String s = latin.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        String[][] folds = {
                {"tch", "ch"}, {"ch", "C"}, {"sh", "S"}, {"th", "s"}, {"ph", "f"}, {"ck", "k"},
                {"c", "k"}, {"q", "k"}, {"x", "ks"}, {"l", "r"}, {"v", "b"}, {"z", "s"}, {"j", "C"}, {"ts", "s"}};
        for (String[] fold : folds) {
            s = s.replace(fold[0], fold[1]);
        }
        s = s.replaceAll("[aeiouy]", "");
        return s.replaceAll("(.)\\1+", "$1");
    }

    /**
     * 우리 이름이 가타카나 위주이고 Google 이름이 영어 위주일 때만 0이 아닌 값.
     * 자음 뼈대가 4글자 이상이고 한쪽이 다른 쪽에 들어 있으면 1.0 (「フグレン」↔「Fuglen Coffee Roasters」),
     * 아니면 두 글자 조각 Dice 계수
     */
    static double loanwordSimilarity(String ours, String google) {
        if (!isMostlyKatakana(ours) || !isMostlyLatin(google)) return 0;
        String x = skeleton(romanize(ours));
        String y = skeleton(Normalizer.normalize(google, Normalizer.Form.NFKC));
        if (x.length() < 2 || y.length() < 2) return 0;
        if (Math.min(x.length(), y.length()) >= 4 && (x.contains(y) || y.contains(x))) return 1.0;

        Set<String> gx = bigrams(x);
        Set<String> gy = bigrams(y);
        Set<String> common = new HashSet<>(gx);
        common.retainAll(gy);
        return 2.0 * common.size() / (gx.size() + gy.size());
    }

    /** 기호·띄어쓰기를 뺀 글자의 60% 이상이 가타카나 */
    static boolean isMostlyKatakana(String name) {
        String s = SYMBOLS.matcher(Normalizer.normalize(name, Normalizer.Form.NFKC)).replaceAll("");
        if (s.isEmpty()) return false;
        long kana = s.chars().filter(ch -> (ch >= 0x30A1 && ch <= 0x30FA) || ch == 0x30FC).count();
        return kana >= s.length() * 0.6;
    }

    /** 기호·띄어쓰기를 뺀 글자의 60% 이상이 영문자 */
    static boolean isMostlyLatin(String name) {
        String s = SYMBOLS.matcher(Normalizer.normalize(name, Normalizer.Form.NFKC)).replaceAll("");
        if (s.isEmpty()) return false;
        long latin = s.chars().filter(ch -> (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')).count();
        return latin >= s.length() * 0.6;
    }

    /** 둘 다 지점 이름이 있는데 서로 다르면 다른 가게 (「グランスタ」↔「グランスタ東京」처럼 한쪽이 포함되면 같은 곳으로 본다) */
    static boolean branchConflict(String a, String b) {
        String x = branchOf(a);
        String y = branchOf(b);
        if (x == null || y == null) return false;
        return !x.contains(y) && !y.contains(x);
    }
}
