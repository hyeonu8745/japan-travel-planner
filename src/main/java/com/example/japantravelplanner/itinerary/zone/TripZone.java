package com.example.japantravelplanner.itinerary.zone;

import com.example.japantravelplanner.global.util.GeoUtils;
import com.example.japantravelplanner.trip.region.City;
import lombok.Getter;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 하루 일정의 단위가 되는 동네 지역. 도쿄 7개 + 확장 18개 도시 (11-18, 관광지 분포를 보고 중심·반경을 정함).
 * 구(ward) 경계가 아니라 여행자가 하루에 묶어서 다니는 범위로 정의했다.
 * 중심 좌표는 관광지 119곳의 분포를 보고 조정 (아키하바라·간다는 구단시타 쪽을 포함하도록 서쪽으로 이동)
 *
 * 지역마다 도시(City)를 붙였다 (권역 → 도시 → 지역). 일정은 숙소 도시의 지역만 쓴다.
 * 도시끼리는 지역 반경이 겹치지 않는다 (TripZoneTest). 대화에서 지역을 알아보는 별칭과
 * "멀어서 관심사가 없으면 점수 절반"(오다이바) 표시도 DayZonePlanner 에 박혀 있던 것을 지역으로 옮겼다.
 */
@Getter
public enum TripZone {

    ASAKUSA_UENO("아사쿠사·우에노", 35.7140, 139.7860, City.TOKYO, false,
            List.of("아사쿠사", "우에노", "야나카", "센소지")),
    GINZA_TOKYO("긴자·도쿄역", 35.6765, 139.7660, City.TOKYO, false,
            List.of("긴자", "도쿄역", "마루노우치", "니혼바시", "쓰키지", "쓰키시마", "황궁")),
    AKIHABARA_KANDA("아키하바라·간다", 35.6950, 139.7620, City.TOKYO, false,
            List.of("아키하바라", "간다", "진보초", "구단시타", "야스쿠니",
                    "피규어", "프라모델", "건프라", "애니")),   // 피규어·애니 숍이 모인 곳 (Wish.HOBBY)
    SHINJUKU("신주쿠", 35.6890, 139.7040, City.TOKYO, false,
            List.of("신주쿠", "가부키초", "도청")),
    SHIBUYA_HARAJUKU("시부야·하라주쿠", 35.6660, 139.7010, City.TOKYO, false,
            List.of("시부야", "하라주쿠", "오모테산도", "메이지", "요요기")),
    ROPPONGI_TOWER("롯폰기·도쿄타워", 35.6605, 139.7390, City.TOKYO, false,
            List.of("롯폰기", "도쿄타워", "도쿄 타워", "아자부")),
    ODAIBA("오다이바", 35.6300, 139.7750, City.TOKYO, true,
            List.of("오다이바", "레인보우")),

    // ---- OSAKA ----
    NAMBA("난바·도톤보리", 34.668, 135.503, City.OSAKA, false,
            List.of("난바", "도톤보리", "신사이바시", "구로몬", "아메리카무라", "닛폰바시", "호젠지")),
    UMEDA("우메다·나카노시마", 34.7, 135.495, City.OSAKA, false,
            List.of("우메다", "오사카역", "나카노시마", "공중정원")),
    OSAKA_CASTLE("오사카성", 34.686, 135.526, City.OSAKA, false,
            List.of("오사카성", "오사카 성", "모리노미야")),
    TENNOJI("덴노지·신세카이", 34.65, 135.511, City.OSAKA, false,
            List.of("덴노지", "신세카이", "쓰텐카쿠", "아베노", "하루카스", "시텐노지")),
    OSAKA_BAY("베이 에어리어·USJ", 34.66, 135.432, City.OSAKA, false,
            List.of("유니버설", "USJ", "유니버셜", "가이유칸", "덴포잔", "베이 에어리어")),
    SUMIYOSHI("스미요시", 34.613, 135.493, City.OSAKA, false,
            List.of("스미요시"), 2000),

    // ---- KYOTO ----
    GION("기온·히가시야마", 34.998, 135.779, City.KYOTO, false,
            List.of("기온", "기요미즈", "히가시야마", "니넨자카", "산넨자카", "야사카", "하나미코지")),
    KYOTO_STATION("교토역·후시미", 34.979, 135.765, City.KYOTO, false,
            List.of("교토역", "후시미", "이나리", "교토 타워", "산주산겐도")),
    KAWARAMACHI("시내·니조성", 35.012, 135.757, City.KYOTO, false,
            List.of("가와라마치", "니시키", "시조", "니조", "고쇼", "폰토초")),
    KINKAKUJI("금각사·기타노", 35.033, 135.728, City.KYOTO, false,
            List.of("금각사", "킨카쿠지", "료안지", "기타노", "닌나지")),
    ARASHIYAMA("아라시야마", 35.014, 135.676, City.KYOTO, false,
            List.of("아라시야마", "사가노", "대나무숲", "죽림", "도게쓰", "덴류지")),
    UJI("우지·뵤도인", 34.891, 135.8, City.KYOTO, false,
            List.of("우지", "뵤도인", "닌텐도")),
    GINKAKUJI("은각사·오카자키", 35.018, 135.793, City.KYOTO, false,
            List.of("은각사", "긴카쿠지", "철학의 길", "난젠지", "헤이안", "오카자키")),

    // ---- KOBE ----
    SANNOMIYA("산노미야·기타노", 34.697, 135.193, City.KOBE, false,
            List.of("산노미야", "기타노", "이진칸", "누노비키", "이쿠타")),
    HARBORLAND("하버랜드·모토마치", 34.684, 135.185, City.KOBE, false,
            List.of("하버랜드", "모토마치", "메리켄", "난킨마치", "차이나타운", "포트타워", "고베항")),
    ROKKO_ARIMA("롯코산·아리마 온천", 34.765, 135.23, City.KOBE, false,
            List.of("롯코", "아리마", "마야산"), 4500),

    // ---- NARA ----
    NARA_PARK("나라 공원", 34.685, 135.84, City.NARA, false,
            List.of("나라 공원", "나라공원", "도다이지", "가스가", "고후쿠지", "사슴", "나라마치", "와카쿠사")),
    NISHINOKYO("니시노쿄·헤이조궁", 34.68, 135.787, City.NARA, false,
            List.of("니시노쿄", "헤이조", "야쿠시지", "도쇼다이지")),
    IKARUGA("호류지", 34.614, 135.736, City.NARA, false,
            List.of("호류지", "이카루가")),

    // ---- YOKOHAMA ----
    MINATOMIRAI("미나토미라이", 35.456, 139.633, City.YOKOHAMA, false,
            List.of("미나토미라이", "랜드마크", "아카렌가", "컵누들", "코스모")),
    CHINATOWN("차이나타운·야마시타", 35.443, 139.648, City.YOKOHAMA, false,
            List.of("차이나타운", "중화가", "야마시타", "모토마치", "야마테", "마린타워", "산케이엔"), 3500),

    // ---- KAMAKURA ----
    KAMAKURA_STATION("가마쿠라역·쓰루가오카", 35.322, 139.553, City.KAMAKURA, false,
            List.of("가마쿠라역", "쓰루가오카", "하치만구", "고마치", "기타카마쿠라", "겐초지", "엔가쿠지")),
    HASE("하세·대불", 35.314, 139.535, City.KAMAKURA, false,
            List.of("하세", "대불", "고토쿠인")),
    ENOSHIMA("에노시마", 35.302, 139.484, City.KAMAKURA, false,
            List.of("에노시마", "에노덴", "가마쿠라고교", "슬램덩크", "시치리가하마")),

    // ---- HAKONE ----
    YUMOTO("하코네유모토·오다와라", 35.233, 139.103, City.HAKONE, false,
            List.of("유모토", "하코네유모토", "오다와라"), 5000),
    GORA("고라·오와쿠다니", 35.246, 139.035, City.HAKONE, false,
            List.of("고라", "오와쿠다니", "조각의 숲", "로프웨이", "센고쿠하라", "포라")),
    ASHINOKO("아시노코", 35.205, 139.02, City.HAKONE, false,
            List.of("아시노코", "하코네 신사", "모토하코네", "해적선", "세키쇼")),

    // ---- KAWAGUCHIKO ----
    LAKE_KAWAGUCHI("가와구치코 호수", 35.515, 138.76, City.KAWAGUCHIKO, false,
            List.of("가와구치코", "오이시 공원", "가치카치", "호수"), 4500),
    SAIKO("사이코·이야시노사토", 35.499, 138.68, City.KAWAGUCHIKO, false,
            List.of("사이코", "이야시노사토")),
    FUJIYOSHIDA("후지요시다·후지큐", 35.485, 138.79, City.KAWAGUCHIKO, false,
            List.of("후지큐", "하이랜드", "아라쿠라야마", "추레이토", "후지요시다", "센겐")),
    OSHINO("오시노 핫카이", 35.46, 138.833, City.KAWAGUCHIKO, false,
            List.of("오시노", "핫카이")),

    // ---- NAGOYA ----
    NAGOYA_STATION("나고야역·노리타케", 35.175, 136.88, City.NAGOYA, false,
            List.of("나고야역", "메이에키", "노리타케", "도요타", "미드랜드")),
    SAKAE("사카에·오스", 35.165, 136.905, City.NAGOYA, false,
            List.of("사카에", "오스 상점가", "오스칸논", "오아시스", "미라이 타워", "히사야")),
    NAGOYA_CASTLE("나고야성·도쿠가와엔", 35.185, 136.915, City.NAGOYA, false,
            List.of("나고야성", "나고야 성", "도쿠가와")),
    ATSUTA("아쓰타", 35.126, 136.909, City.NAGOYA, false,
            List.of("아쓰타")),
    NAGOYA_PORT("나고야항·레고랜드", 35.07, 136.86, City.NAGOYA, false,
            List.of("나고야항", "수족관", "레고", "리니어", "철도관"), 3500),

    // ---- KANAZAWA ----
    KENROKUEN("겐로쿠엔·가나자와성", 36.562, 136.66, City.KANAZAWA, false,
            List.of("겐로쿠엔", "가나자와성", "21세기", "오야마")),
    HIGASHI_CHAYA("히가시차야·오미초 시장", 36.572, 136.664, City.KANAZAWA, false,
            List.of("히가시차야", "오미초", "가즈에마치")),
    NAGAMACHI("나가마치·니시차야", 36.56, 136.648, City.KANAZAWA, false,
            List.of("나가마치", "무사 저택", "닌자데라", "묘류지", "니시차야")),

    // ---- TAKAYAMA ----
    TAKAYAMA_OLD_TOWN("다카야마 옛 거리", 36.141, 137.255, City.TAKAYAMA, false,
            List.of("산마치", "옛 거리", "진야", "아침시장", "미야가와", "히다노사토")),

    // ---- HIROSHIMA ----
    PEACE_PARK("평화기념공원", 34.394, 132.454, City.HIROSHIMA, false,
            List.of("평화공원", "평화기념", "원폭", "혼도리")),
    HIROSHIMA_CASTLE("히로시마성·슛케이엔", 34.401, 132.463, City.HIROSHIMA, false,
            List.of("히로시마성", "슛케이엔")),
    MIYAJIMA("미야지마", 34.293, 132.32, City.HIROSHIMA, false,
            List.of("미야지마", "이쓰쿠시마", "미센")),

    // ---- FUKUOKA ----
    HAKATA("하카타·나카스", 33.592, 130.411, City.FUKUOKA, false,
            List.of("하카타", "나카스", "캐널", "구시다", "야타이")),
    TENJIN("덴진·오호리 공원", 33.587, 130.385, City.FUKUOKA, false,
            List.of("덴진", "오호리", "후쿠오카성", "마이즈루")),
    MOMOCHI("모모치·후쿠오카 타워", 33.594, 130.354, City.FUKUOKA, false,
            List.of("모모치", "후쿠오카 타워", "시사이드")),
    DAZAIFU("다자이후", 33.52, 130.536, City.FUKUOKA, false,
            List.of("다자이후", "덴만구")),

    // ---- BEPPU ----
    KANNAWA("간나와·지옥 순례", 33.318, 131.472, City.BEPPU, false,
            List.of("간나와", "지옥", "묘반")),
    BEPPU_STATION("벳푸역", 33.28, 131.5, City.BEPPU, false,
            List.of("벳푸역", "다케가와라", "라쿠텐치", "벳푸 타워")),

    // ---- YUFUIN ----
    YUFUIN_TOWN("유후인", 33.264, 131.367, City.YUFUIN, false,
            List.of("유후인", "긴린코", "유노쓰보", "플로럴"), 3500),

    // ---- SAPPORO ----
    SAPPORO_CENTER("오도리·스스키노·삿포로역", 43.062, 141.353, City.SAPPORO, false,
            List.of("오도리", "스스키노", "다누키코지", "시계탑", "TV타워", "니조", "삿포로역", "맥주 박물관")),
    MOIWA("모이와산·오쿠라야마", 43.037, 141.3, City.SAPPORO, false,
            List.of("모이와", "오쿠라야마"), 3500),
    SHIROI_KOIBITO("시로이 고이비토 파크", 43.089, 141.272, City.SAPPORO, false,
            List.of("시로이 고이비토", "시로이"), 2000),
    MOERENUMA("모에레누마 공원", 43.122, 141.428, City.SAPPORO, false,
            List.of("모에레"), 2000),
    HITSUJIGAOKA("히쓰지가오카 전망대", 42.998, 141.395, City.SAPPORO, false,
            List.of("히쓰지가오카"), 2000),

    // ---- OTARU ----
    OTARU_CANAL("오타루 운하", 43.198, 141.002, City.OTARU, false,
            List.of("오타루", "운하", "오르골", "사카이마치", "기타이치", "덴구야마"), 4500),

    // ---- NAHA ----
    KOKUSAI_DORI("국제거리·쓰보야", 26.215, 127.685, City.NAHA, false,
            List.of("국제거리", "고쿠사이", "마키시", "쓰보야", "공설시장")),
    SHURI("슈리성", 26.217, 127.718, City.NAHA, false,
            List.of("슈리", "시키나엔")),
    SENAGA("세나가지마", 26.177, 127.641, City.NAHA, false,
            List.of("우미카지", "세나가"));

    /** 지역 반경 기본값: 중심에서 이보다 멀면 지역 밖 (자동 일정 후보에서 제외) */
    public static final double MAX_DISTANCE_M = 3_000;

    private final String label;
    private final double centerLat;
    private final double centerLng;
    private final City city;
    /** 중심에서 떨어진 곳: 야경·쇼핑 관심사가 없으면 지역 점수를 절반으로 (오다이바) */
    private final boolean remote;
    /** 대화에서 이 지역을 알아보는 말 */
    private final List<String> aliases;
    /** 지역 반경(미터). 작은 도시는 관광지가 넓게 퍼져 있어 크게 둔다 (가와구치코 호수 4.5km) */
    private final double radiusMeters;

    TripZone(String label, double centerLat, double centerLng, City city, boolean remote, List<String> aliases) {
        this(label, centerLat, centerLng, city, remote, aliases, MAX_DISTANCE_M);
    }

    TripZone(String label, double centerLat, double centerLng, City city, boolean remote, List<String> aliases,
             double radiusMeters) {
        this.label = label;
        this.centerLat = centerLat;
        this.centerLng = centerLng;
        this.city = city;
        this.remote = remote;
        this.aliases = aliases;
        this.radiusMeters = radiusMeters;
    }

    /** 이 도시의 지역들 */
    public static List<TripZone> inCity(City city) {
        return Arrays.stream(values()).filter(zone -> zone.city == city).toList();
    }

    /** 좌표가 반경 안에 들어가는 지역 중 중심이 가장 가까운 곳 (어느 지역 반경에도 없으면 빈 값) */
    public static Optional<TripZone> nearest(double lat, double lng) {
        return Arrays.stream(values())
                .filter(zone -> zone.distanceTo(lat, lng) <= zone.radiusMeters)
                .min(Comparator.comparingDouble(zone -> zone.distanceTo(lat, lng)));
    }

    /**
     * 그날 실제로 도는 지역: 관광지가 있는 지역만, 중심 지역에 관광지가 있으면 맨 앞에.
     * 관광지가 하나도 없으면(첫날 저녁만 있는 날 등) 중심 지역.
     * 3일차 중심이 롯폰기였지만 롯폰기 관광지는 2일차가 다 써서 시부야만 돌았는데 "롯폰기 + 시부야"로 보였다
     * @param stopZones 관광지 지역 (방문 순서대로, 중복 가능)
     */
    public static java.util.List<TripZone> visited(TripZone primary, java.util.Collection<TripZone> stopZones) {
        java.util.LinkedHashSet<TripZone> zones = new java.util.LinkedHashSet<>(stopZones);
        zones.remove(null);
        if (zones.isEmpty()) return primary == null ? java.util.List.of() : java.util.List.of(primary);
        if (zones.remove(primary)) {
            java.util.List<TripZone> ordered = new java.util.ArrayList<>();
            ordered.add(primary);
            ordered.addAll(zones);
            return java.util.List.copyOf(ordered);
        }
        return java.util.List.copyOf(zones);
    }

    /** 여러 지역을 화면·설명용 한 줄로 ("아사쿠사·우에노 + 아키하바라·간다") */
    public static String labelOf(java.util.Collection<TripZone> zones) {
        return zones.stream().map(TripZone::getLabel).collect(java.util.stream.Collectors.joining(" + "));
    }

    /** 이 지역 중심에서 다른 지역 중심까지의 거리(미터) */
    public double distanceTo(TripZone other) {
        return distanceTo(other.centerLat, other.centerLng);
    }

    /** 이 지역 중심에서 주어진 좌표까지의 거리(미터) */
    public double distanceTo(double lat, double lng) {
        return GeoUtils.distanceMeters(centerLat, centerLng, lat, lng);
    }
}