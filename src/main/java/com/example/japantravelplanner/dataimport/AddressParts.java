package com.example.japantravelplanner.dataimport;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 일본 주소 글자에서 도도부현과 시·구 이름을 뽑는다 (예: 東京都千代田区飯田橋… → 東京, 千代田区).
 * 예전 도쿄 데이터의 prefecture·municipalities_1 칸과 같은 모양으로 채워서, 구 이름을 쓰는 기존 검색이 계속 동작하게 한다.
 * 정령지정도시는 구까지 (大阪府大阪市北区 → 大阪市北区).
 */
public record AddressParts(String prefecture, String municipality) {

    private static final Pattern PREFECTURE = Pattern.compile("^(東京都|北海道|(?:京都|大阪)府|.{2,3}?県)");
    // 앞에서부터 차례로: 시+구(정령지정도시), 구(도쿄 23구), 시, 군+정·촌, 정·촌
    private static final List<Pattern> MUNICIPALITY = List.of(
            Pattern.compile("^([^市区町村]+?市[^市区町村]+?区)"),
            Pattern.compile("^([^市区]+?区)"),
            Pattern.compile("^([^市区]+?市)"),
            Pattern.compile("^([^郡]+?郡[^町村]+?[町村])"),
            Pattern.compile("^([^町村]+?[町村])"));

    private static final AddressParts EMPTY = new AddressParts(null, null);

    /** 주소에서 나눈 결과. 주소가 비었거나 모양을 모르면 두 칸 다 null */
    public static AddressParts of(String address) {
        if (address == null || address.isBlank()) return EMPTY;
        String a = address.strip();
        Matcher p = PREFECTURE.matcher(a);
        if (!p.find()) return EMPTY;
        String pref = p.group(1);
        String rest = a.substring(pref.length());
        String municipality = null;
        for (Pattern m : MUNICIPALITY) {
            Matcher c = m.matcher(rest);
            if (c.find()) {
                municipality = c.group(1);
                break;
            }
        }
        return new AddressParts(shortPrefecture(pref), municipality);
    }

    /** 예전 데이터처럼 도·부·현 글자를 뗀다 (東京都 → 東京, 北海道는 그대로) */
    private static String shortPrefecture(String pref) {
        if (pref.equals("北海道")) return pref;
        return pref.substring(0, pref.length() - 1);
    }
}
