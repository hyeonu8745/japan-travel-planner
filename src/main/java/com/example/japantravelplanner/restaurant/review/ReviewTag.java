package com.example.japantravelplanner.restaurant.review;

import java.util.List;
import java.util.Locale;

/**
 * 리뷰에서 찾아 표시하는 경험 종류 (10-10 사용자 결정: 식당만, 이 4가지).
 *
 * 근거 구절에 꼭 들어 있어야 하는 말(keywords)을 둔다. gpt-4o-mini가 맛·대기 불만을 "불친절"로,
 * "다른 손님은 4인석에 앉혔다"를 "차별"로 잘못 표시한 것을 실제 응답(10-10, CAFE AALIYA)에서 확인해서
 * AI 판정만 믿지 않고 서버에서 한 번 더 거른다. 특히 차별은 작성자가 국적·외국인을 직접 언급한 경우만 남긴다.
 */
public enum ReviewTag {

    /** 한국인·외국인이라는 이유로 차별·무시·다른 대우를 받았다는 경험 (작성자가 국적·외국인을 직접 언급) */
    DISCRIMINATION("차별·혐한 경험", List.of(
            "한국", "외국", "국적", "차별", "혐한", "관광객", "일본인",
            "korean", "korea", "foreign", "tourist", "韓国", "外国", "差別")),

    /** 직원 태도가 불친절하거나 무례했다는 경험 (직원·응대에 대한 말이 있어야 함) */
    UNFRIENDLY("불친절 경험", List.of(
            "직원", "점원", "종업원", "사장", "주인", "알바", "서버", "태도", "응대", "무시", "불친절", "무례", "퉁명",
            "staff", "rude", "waiter", "店員", "スタッフ", "態度", "接客")),

    /** 음식·식기·가게의 청결 문제 */
    HYGIENE("위생 문제", List.of(
            "위생", "더럽", "더러", "지저분", "벌레", "바퀴", "머리카락", "청결", "상한", "곰팡",
            "dirty", "hygiene", "cockroach", "汚", "虫", "不衛生")),

    /** 한국어 메뉴, 한국어 가능 직원, 한국인 손님에게 친절함, 혼밥 편함 등 한국인 여행자에게 좋은 점 */
    KOREAN_FRIENDLY("한국인에게 좋은 점", List.of(
            "한국", "한글", "혼밥", "혼자 가기", "혼자서도", "혼자 오기", "1인석", "1인 메뉴",
            "korean", "韓国"));   // "혼자"만으로는 안 된다 ("혼자왔는데 4인석에 안내" 같은 문장이 걸린다)

    private final String label;
    private final List<String> keywords;

    ReviewTag(String label, List<String> keywords) {
        this.label = label;
        this.keywords = keywords;
    }

    public String label() {
        return label;
    }

    /** 주의가 필요한 경험인지 (좋은 점은 아님) */
    public boolean caution() {
        return this != KOREAN_FRIENDLY;
    }

    /**
     * 글에 이 종류의 핵심 말이 들어 있는지 (없으면 AI가 잘못 붙인 것으로 보고 버린다).
     * 띄어쓰기는 무시한다 (분석기는 띄어쓰기를 뺀 리뷰에서 근거 주변을 잘라 넘긴다)
     */
    public boolean supportedBy(String text) {
        if (text == null) return false;
        String target = text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        return keywords.stream().anyMatch(k -> target.contains(k.replaceAll("\\s+", "").toLowerCase(Locale.ROOT)));
    }

    /** AI가 준 문자열 → 태그. 모르는 값이면 null */
    public static ReviewTag from(String value) {
        if (value == null) return null;
        for (ReviewTag tag : values()) {
            if (tag.name().equals(value.trim())) return tag;
        }
        return null;
    }
}
