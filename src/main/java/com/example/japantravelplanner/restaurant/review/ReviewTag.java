package com.example.japantravelplanner.restaurant.review;

import java.util.List;
import java.util.Locale;

/**
 * 리뷰에서 찾아 표시하는 경험 종류 (10-10 사용자 결정: 식당만, 이 4가지).
 *
 * AI가 붙인 표시를 그대로 믿지 않고, 근거 주변 글이 종류별 규칙(rules) 중 하나를 만족해야 남긴다.
 * 규칙은 "이 말들 중 하나 + 저 말들 중 하나"처럼 말 묶음(Group)을 모두 만족해야 하는 조합이다.
 *
 * 10-10 실제 식당 20곳 표본에서 단어 하나만 보던 방식이 13건 중 9건을 잘못 남겼다
 * ("여러 외국인들이 대기해서"→차별, "종업원분들께서도 굉장히 친절하시고"→불친절, "한국인에게도 맛있는"→좋은 점).
 * 그래서 차별은 국적 말 + 다르게 대한 말, 불친절은 직원 말 + 나쁜 태도 말, 좋은 점은 한국어 메뉴·한국어 직원·혼밥처럼
 * 실제로 도움이 되는 말이 있어야 한다. "불친절하지 않았어요"처럼 부정된 말은 세지 않고, 오히려 그 종류를 버린다(denial).
 */
public enum ReviewTag {

    /** 한국인·외국인이라는 이유로 차별·무시·다른 대우를 받았다는 경험 */
    DISCRIMINATION("차별·혐한 경험", Words.DISCRIMINATION_WORDS, List.of(
            Rule.of(Group.negatable(Words.DISCRIMINATION_WORDS)),
            Rule.of(Group.plain(Words.NATIONALITY), Group.negatable(Words.TREATMENT)))),

    /** 직원 태도가 불친절하거나 무례했다는 경험 */
    UNFRIENDLY("불친절 경험", Words.UNFRIENDLY_WORDS, List.of(
            Rule.of(Group.negatable(Words.UNFRIENDLY_WORDS)),
            Rule.of(Group.plain(Words.STAFF), Group.negatable(Words.NEGATIVE_ATTITUDE)))),

    /** 음식·식기·가게의 청결 문제 */
    HYGIENE("위생 문제", List.of(), List.of(
            Rule.of(Group.negatable(Words.HYGIENE_WORDS)),
            Rule.of(Group.plain(Words.HYGIENE_SUBJECT), Group.negatable(Words.HYGIENE_DOUBT)))),

    /** 한국어 메뉴, 한국어 가능 직원, 한국인 손님에게 친절함, 혼밥 편함 등 한국인 여행자에게 좋은 점 */
    KOREAN_FRIENDLY("한국인에게 좋은 점", List.of(), List.of(
            Rule.of(Group.negatable(Words.KOREAN_SUPPORT)),
            Rule.of(Group.plain(Words.KOREAN_GUEST), Group.negatable(Words.WELCOME))));

    private final String label;
    private final List<String> denial;
    private final List<Rule> rules;

    ReviewTag(String label, List<String> denial, List<Rule> rules) {
        this.label = label;
        this.denial = denial;
        this.rules = rules;
    }

    public String label() {
        return label;
    }

    /** 주의가 필요한 경험인지 (좋은 점은 아님) */
    public boolean caution() {
        return this != KOREAN_FRIENDLY;
    }

    /**
     * 글이 이 종류의 규칙 중 하나를 만족하는지 (못 하면 AI가 잘못 붙인 것으로 보고 버린다).
     * 띄어쓰기·대소문자는 무시한다. "불친절하진 않았다"처럼 이 종류를 부정하는 말이 있으면 false.
     */
    public boolean supportedBy(String text) {
        if (text == null) return false;
        String target = squeeze(text);
        if (denial.stream().anyMatch(word -> hasNegated(target, squeeze(word)))) return false;
        return rules.stream().anyMatch(rule -> rule.matches(target));
    }

    /** AI가 준 문자열 → 태그. 모르는 값이면 null */
    public static ReviewTag from(String value) {
        if (value == null) return null;
        for (ReviewTag tag : values()) {
            if (tag.name().equals(value.trim())) return tag;
        }
        return null;
    }

    static String squeeze(String text) {
        return text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    /** 말 바로 뒤가 "~지 않", "~은 아니", "~없", "~못"처럼 부정인지 (띄어쓰기를 뺀 글 기준) */
    static boolean negatedAfter(String target, int end) {
        String after = target.substring(end, Math.min(target.length(), end + Words.NEGATION_WINDOW));
        for (String safe : Words.NOT_NEGATION) after = after.replace(safe, "");
        for (String negation : Words.NEGATIONS) if (after.contains(negation)) return true;
        String near = after.substring(0, Math.min(after.length(), Words.NEAR_NEGATION_WINDOW));
        for (String negation : Words.NEAR_NEGATIONS) if (near.contains(negation)) return true;
        return false;
    }

    private static boolean hasNegated(String target, String word) {
        for (int at = target.indexOf(word); at >= 0; at = target.indexOf(word, at + 1)) {
            if (negatedAfter(target, at + word.length())) return true;
        }
        return false;
    }

    /** 말 묶음. 그중 하나가 글에 있으면 만족. negatable이면 바로 뒤가 부정인 경우는 세지 않는다 */
    record Group(List<String> words, boolean negatable) {

        static Group plain(List<String> words) {
            return new Group(words, false);
        }

        static Group negatable(List<String> words) {
            return new Group(words, true);
        }

        boolean matches(String target) {
            for (String raw : words) {
                String word = squeeze(raw);
                for (int at = target.indexOf(word); at >= 0; at = target.indexOf(word, at + 1)) {
                    // "친절"로 시작하는 말이 "불친절"의 일부로 찾아진 것은 반대 뜻이라 세지 않는다
                    if (word.startsWith("친절") && at > 0 && target.charAt(at - 1) == '불') continue;
                    if (negatable && negatedAfter(target, at + word.length())) continue;
                    return true;
                }
            }
            return false;
        }
    }

    /** 말 묶음을 모두 만족해야 하는 규칙 */
    record Rule(List<Group> groups) {

        static Rule of(Group... groups) {
            return new Rule(List.of(groups));
        }

        boolean matches(String target) {
            return groups.stream().allMatch(group -> group.matches(target));
        }
    }

    /** 종류별 말 목록. 띄어쓰기는 비교할 때 빼므로 읽기 편하게 띄어 쓴다 */
    static final class Words {

        private Words() {
        }

        /** 국적·외국인을 가리키는 말 (맨 "한국"은 넣지 않는다: "한국어 메뉴", "한국인 입맛"이 걸린다) */
        static final List<String> NATIONALITY = List.of(
                "한국인", "한국 사람", "한국분", "한국 손님", "한국어로", "한국말로", "한국어를", "한국말을", "외국인", "외국 사람", "외국 손님", "외국분", "국적",
                "관광객", "일본인", "일본 사람", "korean", "foreign", "tourist", "韓国人", "外国人", "観光客");
        /** 이것만 있어도 차별 이야기로 보는 말 */
        static final List<String> DISCRIMINATION_WORDS = List.of(
                "차별", "혐한", "인종", "差別", "嫌韓", "discriminat", "racis");
        /** 국적 말과 함께 있어야 하는 '다르게 대했다' 말 ("외국인이 많아 대기"는 이게 없어서 걸리지 않는다) */
        static final List<String> TREATMENT = List.of(
                "무시", "다르게", "다른 대우", "대우", "태도가 바뀌", "태도가 달라", "표정이 굳", "표정이 바뀌", "퉁명", "불친절", "무례", "구석", "거절",
                "거부", "안 받", "받지 않", "돌려보내", "돌려보냈", "쫓아", "쫓겨", "내쫓", "에게만", "한테만", "만 따로", "비웃", "째려", "노려", "눈치",
                "불쾌", "ignor", "rude", "refus", "無視", "失礼");
        /** 직원·응대를 가리키는 말 */
        static final List<String> STAFF = List.of(
                "직원", "점원", "종업원", "사장", "주인", "알바", "서버", "태도", "응대", "접객", "서비스", "staff", "waiter", "waitress",
                "server", "店員", "スタッフ", "態度", "接客");
        /** 이것만 있어도 불친절 이야기로 보는 말 */
        static final List<String> UNFRIENDLY_WORDS = List.of(
                "불친절", "무례", "싸가지", "rude", "unfriendly", "impolite", "不親切", "失礼");
        /** 직원 말과 함께 있어야 하는 나쁜 태도 말 ("직원분들도 친절하시고"는 이게 없어서 걸리지 않는다) */
        static final List<String> NEGATIVE_ATTITUDE = List.of(
                "퉁명", "무뚝뚝", "무시", "귀찮", "짜증", "화를", "화내", "소리를 지르", "소리 질", "툭", "쾅", "던지", "던져", "던졌", "최악",
                "기분 나", "기분이 나", "기분이 상", "기분 상", "노려", "째려", "재촉", "쫓아", "눈치", "불쾌", "성의 없", "성의가 없", "쌀쌀", "냉정",
                "태도가 별로", "응대가 별로", "서비스가 별로", "태도가 바뀌", "태도가 달라", "친절하지 않", "친절하진 않", "친절하지도 않", "친절한 편은 아니",
                "친절과는 거리", "안 친절", "冷た", "態度が悪");
        /** 이것만 있어도 위생 문제로 보는 말 ("청결"은 칭찬에도 쓰여서 여기 넣지 않는다) */
        static final List<String> HYGIENE_WORDS = List.of(
                "더럽", "더러", "지저분", "벌레", "바퀴", "머리카락", "상한", "곰팡", "비위생", "불결", "끈적", "하수구", "악취", "dirty", "filthy",
                "cockroach", "unhygien", "汚", "虫", "不衛生", "髪の毛");
        /** 위생·청결 자체를 가리키는 말 */
        static final List<String> HYGIENE_SUBJECT = List.of(
                "위생", "청결");
        /** 위생·청결 말과 함께 있어야 하는 '좋지 않다' 말 */
        static final List<String> HYGIENE_DOUBT = List.of(
                "별로", "안 좋", "않 좋", "나쁘", "엉망", "문제", "걱정", "의심", "아쉽", "그닥", "그다지", "글쎄", "🤔", "불량", "꽝", "떨어");
        /** 이것만 있어도 한국인에게 좋은 점으로 보는 말 (맨 "한국"·"한국인"은 넣지 않는다: 맛·인기 이야기가 걸린다) */
        static final List<String> KOREAN_SUPPORT = List.of(
                "한국어", "한글", "한국말", "한국분", "한국인 직원", "한국인 사장", "한국인 알바", "한국인 점원", "혼밥", "혼자 가기", "혼자서도", "혼자 오기",
                "혼자 먹기", "혼자 방문", "1인석", "1인 좌석", "1인 메뉴", "korean menu", "韓国語", "ハングル");
        /** 한국인 손님을 대한 이야기임을 나타내는 말 */
        static final List<String> KOREAN_GUEST = List.of(
                "한국인에게", "한국인한테", "한국인 손님", "한국 손님", "한국 사람에게", "한국 사람한테", "한국인이라고", "한국에서 왔", "한국인인 걸", "한국분들에게",
                "korean guest");
        /** 한국인 손님 말과 함께 있어야 하는 '잘 대해 줬다' 말 */
        static final List<String> WELCOME = List.of(
                "친절", "반겨", "반갑", "환영", "배려", "챙겨");

        /** 이 글자 수 안에 아래 부정 말이 있으면 부정으로 본다 ("불친절하다느껴지진않"까지 닿게 8) */
        static final int NEGATION_WINDOW = 8;
        static final List<String> NEGATIONS = List.of("지않", "진않", "지는않", "지도않", "건아니", "것은아니", "게아니", "은아니", "는아니", "도아니");

        /** "없"·"못"은 흔해서 바로 뒤(5글자)만 본다 ("한글 메뉴는 없어요", "한국어를 못하는") */
        static final int NEAR_NEGATION_WINDOW = 5;
        static final List<String> NEAR_NEGATIONS = List.of("없", "못");

        /** "없"이 들어 있지만 좋은 뜻인 말 ("혼자 가기 부담 없는") */
        static final List<String> NOT_NEGATION = List.of("부담없", "걱정없", "불편없", "불편함없");
    }
}
