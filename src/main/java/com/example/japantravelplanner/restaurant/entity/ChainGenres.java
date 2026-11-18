package com.example.japantravelplanner.restaurant.entity;

import java.util.Map;

/**
 * 체인점 브랜드의 한국어 장르(chain_brands.genre_ko) → 타베로그식 일본어 장르 (콤마 구분).
 * 식당 후보 거르기(피할 음식·알레르기·흡연), 같은 음식 반복 판단, 장르 이름표가 모두 타베로그 장르로 되어 있어서
 * 체인점도 같은 이름으로 맞춘다. 브랜드 표에는 한국어 장르만 있다 (지점마다 반복하지 않게 V5 에서 브랜드 표로 뺐다).
 */
public final class ChainGenres {

    private static final Map<String, String> JA = Map.ofEntries(
            Map.entry("규동", "牛丼"),
            Map.entry("규동·우동", "牛丼,うどん"),
            Map.entry("나가사키 짬뽕", "ちゃんぽん"),
            Map.entry("대만요리(소룡포)", "台湾料理,小籠包"),
            Map.entry("돈카츠·가츠동", "とんかつ,カツ丼"),
            Map.entry("라멘", "ラーメン"),
            Map.entry("라멘·중화", "ラーメン,中華料理"),
            Map.entry("샌드위치", "サンドイッチ"),
            Map.entry("샤부샤부", "しゃぶしゃぶ"),
            Map.entry("샤부샤부 뷔페", "しゃぶしゃぶ,ビュッフェ"),
            Map.entry("샤부샤부·일식", "しゃぶしゃぶ,日本料理"),
            Map.entry("소바", "そば"),
            Map.entry("스시", "寿司"),
            Map.entry("스테이크", "ステーキ"),
            Map.entry("아메리칸", "アメリカ料理"),
            Map.entry("야키니쿠", "焼肉"),
            Map.entry("야키니쿠 무한리필", "焼肉"),
            Map.entry("우동", "うどん"),
            Map.entry("이탈리안", "イタリアン"),
            Map.entry("일식", "日本料理"),
            Map.entry("정식", "定食・食堂"),
            Map.entry("중화요리", "中華料理"),
            Map.entry("중화요리·교자", "中華料理,餃子"),
            Map.entry("철판 스테이크·라이스", "ステーキ,鉄板焼き"),
            Map.entry("치킨", "からあげ,ファーストフード"),
            Map.entry("카레", "カレーライス"),
            Map.entry("텐동", "天丼・天重,天ぷら"),
            Map.entry("파스타", "パスタ"),
            Map.entry("패밀리 레스토랑", "ファミレス"),
            Map.entry("패밀리 레스토랑(이탈리안)", "ファミレス,イタリアン"),
            Map.entry("함박스테이크", "ハンバーグ"),
            Map.entry("햄버거", "ハンバーガー"),
            Map.entry("회전초밥", "回転寿司,寿司"),
            // 카페·디저트 브랜드 (끼니 후보는 아니지만 이름표·거르기에 쓴다)
            Map.entry("카페", "カフェ"),
            Map.entry("카페(킷사텐)", "喫茶店,カフェ"),
            Map.entry("애니 콜라보 카페", "カフェ"),
            Map.entry("도넛", "ドーナツ"),
            Map.entry("버블티", "タピオカ,ジュース"),
            Map.entry("케이크·양과자", "ケーキ,洋菓子"),
            Map.entry("타코야키(간식)", "たこ焼き"));

    /** 정적 메서드만 쓴다 */
    private ChainGenres() {
    }

    /** 한국어 장르 → 일본어 장르 (콤마 구분). 표에 없으면 null */
    public static String of(String genreKo) {
        return genreKo == null ? null : JA.get(genreKo.trim());
    }
}
