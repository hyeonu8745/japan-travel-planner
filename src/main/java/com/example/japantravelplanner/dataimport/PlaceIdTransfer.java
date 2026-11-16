package com.example.japantravelplanner.dataimport;

import com.example.japantravelplanner.global.util.GeoUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 예전 도쿄 관광지(Google Places 로 모은 1,434곳)의 Google 장소 ID를 새 관광지(OSM)에 옮긴다.
 * 장소 ID가 있어야 상세 화면에서 Google 사진·영업시간·리뷰를 받아올 수 있다 (ID 저장은 약관상 허용).
 * 이름이 똑같으면 1.5km, 한쪽이 다른 쪽을 품으면 300m 안에 있을 때만 같은 곳으로 본다. 새 관광지 하나에 ID 하나
 * (이름이 똑같은 짝을 먼저, 그다음 가까운 짝).
 */
public final class PlaceIdTransfer {

    static final double MAX_METERS = 300;

    /** 이름이 똑같으면 넓은 공원·시설도 같은 곳으로 본다 (Google 핀과 OSM 중심이 수백 m 떨어진다) */
    static final double MAX_METERS_EXACT = 1500;

    /** 예전 관광지 한 줄 (attractions_tokyo.csv) */
    public record OldPlace(String placeId, String name, double lat, double lng) {
    }

    /** 새 관광지 한 줄 (이름은 일본어 이름과 영어 이름) */
    public record NewPlace(String osmId, String name, String nameEn, double lat, double lng) {
    }

    private record Pair(String osmId, String placeId, double meters, boolean exact) {
    }

    /** 만들지 않는다 */
    private PlaceIdTransfer() {
    }

    /** 새 관광지 OSM 아이디 → 옮겨 줄 Google 장소 ID */
    public static Map<String, String> match(List<OldPlace> olds, List<NewPlace> news) {
        List<Pair> pairs = new ArrayList<>();
        for (OldPlace o : olds) {
            String on = normalize(o.name());
            if (on.isEmpty()) continue;
            for (NewPlace n : news) {
                double m = GeoUtils.distanceMeters(o.lat(), o.lng(), n.lat(), n.lng());
                if (m > MAX_METERS_EXACT) continue;
                String nn = normalize(n.name());
                String ne = normalize(n.nameEn());
                boolean exact = on.equals(nn) || on.equals(ne);
                if (exact || (m <= MAX_METERS && (sameName(on, nn) || sameName(on, ne)))) {
                    pairs.add(new Pair(n.osmId(), o.placeId(), m, exact));
                }
            }
        }
        // 이름이 똑같은 짝 먼저, 그다음 가까운 짝 (東京タワー 가 東京タワー トップデッキ 보다 먼저)
        pairs.sort(Comparator.comparing((Pair p) -> !p.exact()).thenComparingDouble(Pair::meters));
        Map<String, String> result = new HashMap<>();
        Set<String> usedPlaceIds = new HashSet<>();
        for (Pair p : pairs) {
            if (result.containsKey(p.osmId()) || usedPlaceIds.contains(p.placeId())) continue;
            result.put(p.osmId(), p.placeId());
            usedPlaceIds.add(p.placeId());
        }
        return result;
    }

    /** 같은 이름: 같거나, 두 글자 이상인 한쪽이 다른 쪽에 들어 있다 (浅草寺 ⊂ 金龍山浅草寺) */
    static boolean sameName(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b)) return true;
        String shorter = a.length() <= b.length() ? a : b;
        String longer = shorter == a ? b : a;
        return shorter.length() >= 2 && longer.contains(shorter);
    }

    /** 전각·반각을 맞추고 빈칸·가운뎃점·괄호를 뗀 소문자 */
    static String normalize(String name) {
        if (name == null) return "";
        String n = Normalizer.normalize(name, Normalizer.Form.NFKC).toLowerCase();
        return n.replaceAll("[\\s・･·()（）\\[\\]「」『』\\-ー－]", "");
    }
}
