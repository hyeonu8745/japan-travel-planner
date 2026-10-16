package com.example.japantravelplanner.attraction.policy;

import java.util.regex.Pattern;

/**
 * 한국인 여행자에게 역사적으로 민감한 장소 (10-13 사용자 결정: 전범 합사·침략 미화·한반도 침탈 관련 장소는 추천하지 않는다).
 * 일정 후보·교체·추가 후보(AttractionScorer)에서 뺀다. 확장 데이터용 목록은 data/scripts/sensitive_places.tsv 와 같이 고친다.
 * 「護国寺」(분쿄구의 절)처럼 이름이 비슷한 곳은 걸리지 않게 신사 이름까지 맞춘다.
 */
public final class SensitivePlaces {

    private static final Pattern EXCLUDE = Pattern.compile(String.join("|",
            "靖国神社", "靖國神社", "東京招魂社", "遊就館", "鎮霊社",          // 야스쿠니 (A급 전범 합사)
            "護国神社", "護國神社", "招魂社",                                // 각지 야스쿠니 계열 전몰자 위령 신사
            "殉国七士", "殉國七士", "興亜観音", "興亞觀音",                   // A급 전범 위령
            "東條英機", "東条英機", "大東亜", "大東亞",
            "伊藤博文", "松陰神社", "東郷神社", "東郷元帥", "東郷平八郎",
            "乃木神社", "乃木邸", "乃木大将", "乃木将軍",
            "豊国神社", "豊國神社", "豊国廟", "豊國廟",                        // 도요토미 히데요시 (임진왜란)
            "耳塚", "鼻塚", "玄洋社",
            // 10-16 사용자 결정: 메이지 신궁(메이지 천황 — 한국 강제 병합 시기)과 안의 시설(御苑·鳥居·ミュージアム·聖徳記念絵画館).
            // 외원(外苑, 은행나무길·공원)과 야구장은 신사가 아니라 남긴다.
            "明治神宮(?!外苑|野球場|球場|第二球場)", "聖徳記念絵画館",
            "(?i:meiji[ -]?(jingu|shrine)(?![ -]?(gaien|stadium|baseball)))",
            // 10-16 사용자 결정 (데이터를 훑어 찾은 곳, "있는 건 다 빼줘")
            "乃木公園", "東郷公園",                                          // 노기·도고 옛집 터 (신사는 위에서 이미)
            "清正公", "清正井", "加藤清正",                                  // 가토 기요마사 (임진왜란 장수)
            "北海道神宮", "(?i:hokkaido[ -]?(jingu|shrine))",                 // 메이지 천황을 모신 신사
            "(?<![\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}])昭和館", "National Showa Memorial", // 야스쿠니 옆 국립 쇼와관 (高山·由布院 쇼와 레트로 박물관은 남김)
            "しょうけい館", "戦傷病者史料館",                                  // 상이군인 자료관
            "二・二六事件", "二二六事件",                                     // 1936년 군부 쿠데타 위령상
            "福澤諭吉", "福沢諭吉"));                                       // 탈아론·조선 멸시 발언

    private SensitivePlaces() {
    }

    public static boolean isExcluded(String name) {
        return name != null && EXCLUDE.matcher(name).find();
    }
}
