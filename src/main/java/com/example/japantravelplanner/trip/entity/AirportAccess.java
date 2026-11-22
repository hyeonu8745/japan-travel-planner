package com.example.japantravelplanner.trip.entity;

import com.example.japantravelplanner.trip.region.City;

import java.util.List;
import java.util.Optional;

/**
 * 공항 ↔ 도시 이동. 확장 때 한 공항이 여러 도시(간사이 → 오사카·교토·고베·나라),
 * 한 도시가 여러 공항(고베 = 고베 공항·간사이)이 되므로 "공항 + 도시" 짝마다 둔다 (확장 계획 0-7).
 * 값은 교통비 조사 문서(공식 확인, 11-16·11-18·11-22)의 대표값이다. 값이 없는 짝은 시간 60분·요금 없음으로 계산한다.
 * - 나리타 ↔ 도쿄: 게이세이 액세스 특급 1,270엔·공항버스 1,300엔(약 60~100분) / 스카이라이너 2,570엔(닛포리까지 약 36분)·나리타 익스프레스 3,070엔
 * - 하네다 ↔ 도쿄: 게이큐 시나가와까지 330엔(IC 327엔, 약 15분) / 도쿄 모노레일 + JR 약 660엔
 * - 간사이: 난카이 970 / 라피트 970+700, 하루카 외국인 편도권 교토 2,200, 리무진 교토 2,800·산노미야 2,200·나라 2,400,
 *   베이 셔틀 1,880 + 포트라이너 340. 고베 공항 포트라이너 340
 * - 주부: 메이테쓰 특급 980 / 뮤스카이 1,430, 다카야마는 + 나고야 → 다카야마 고속버스(웹 3,300) / 특급 히다 자유석 5,610
 * - 고마쓰 리무진 1,400, 히로시마 리무진 1,500, 후쿠오카 지하철 260, 벳푸·유후인 고속버스 3,250,
 *   신치토세 JR 1,230 (u시트 +1,000), 오타루 2,040, 나하 유이레일 290~320
 *
 * @param minutes    공항 ↔ 시내 숙소 대중교통 대표 시간 (갈아타기 포함)
 * @param cheapYen   저렴한 방법 1인 요금 (공항 → 시내 역)
 * @param fastYen    빠른 방법 1인 요금
 */
public record AirportAccess(Airport airport, City city, int minutes,
                            int cheapYen, String cheapLabel, int fastYen, String fastLabel) {

    private static final List<AirportAccess> TABLE = List.of(
            new AirportAccess(Airport.NRT, City.TOKYO, 80,
                    1270, "게이세이 액세스 특급·공항버스 (약 60~100분)", 2570, "스카이라이너 (닛포리까지 약 36분)"),
            new AirportAccess(Airport.HND, City.TOKYO, 45,
                    330, "게이큐선 (시나가와까지 약 15분)", 660, "도쿄 모노레일 + JR (약 25~30분)"),
            // 요코하마·가마쿠라·가와구치코 (11-18 조사: 게이큐 400·리무진 800, 나리타 리무진 3,700·N'EX 4,370,
            // 나리타 → 가마쿠라 JR 2,420 (갈아타기 없음)·N'EX 4,150, 나리타 → 가와구치코 후지 익스프레스 6,000)
            new AirportAccess(Airport.HND, City.YOKOHAMA, 35,
                    400, "게이큐선 (요코하마역까지 약 30분)", 800, "리무진 버스 (YCAT까지 약 30~40분)"),
            new AirportAccess(Airport.NRT, City.YOKOHAMA, 110,
                    3700, "리무진 버스 (YCAT까지 약 1시간 30~40분)", 4370, "나리타 익스프레스 (요코하마까지 약 1시간 30분)"),
            new AirportAccess(Airport.NRT, City.KAMAKURA, 155,
                    2420, "JR 쾌속 (갈아타기 없이 약 2시간 35분)", 4150, "나리타 익스프레스 + JR (약 2시간)"),
            // 11-22 에키탄 검색 (사용자 캡처, IC 운임): 하네다 → 가마쿠라 게이큐 + JR 요코하마 경유 837엔 1시간 4분,
            // 나리타 → 하코네유모토 게이세이·도에이·JR 4,419엔 3시간 10분 / 특급 이용 9,547엔 2시간 33분
            new AirportAccess(Airport.HND, City.KAMAKURA, 70,
                    837, "게이큐 + JR (요코하마 환승, 약 1시간 4분)", 837, "게이큐 + JR (요코하마 환승)"),
            new AirportAccess(Airport.NRT, City.HAKONE, 200,
                    4419, "게이세이·도에이·JR + 하코네 등산철도 (약 3시간 10분)", 9547, "특급 이용 (약 2시간 33분)"),
            new AirportAccess(Airport.NRT, City.KAWAGUCHIKO, 210,
                    6000, "후지 익스프레스 고속버스 (전석 예약)", 6000, "후지 익스프레스 고속버스"),
            new AirportAccess(Airport.HND, City.HAKONE, 180,
                    3100, "게이큐 고속버스 (도겐다이까지 약 2시간 30분~3시간)", 3100, "게이큐 고속버스"),
            new AirportAccess(Airport.HND, City.KAWAGUCHIKO, 160,
                    3300, "후지 익스프레스 고속버스 (약 2시간 35~40분, 전석 예약)", 3300, "후지 익스프레스 고속버스"),
            // 간사이
            new AirportAccess(Airport.KIX, City.OSAKA, 60,
                    970, "난카이 공항급행 (난바까지 약 40분)", 1670, "특급 라피트 (난바까지 약 35분)"),
            new AirportAccess(Airport.KIX, City.KYOTO, 90,
                    2200, "JR 하루카 외국인 편도권 (교토까지 약 80분)", 2800, "공항 리무진 버스 (교토역)"),
            new AirportAccess(Airport.KIX, City.KOBE, 65,
                    2200, "공항 리무진 버스 (산노미야까지 약 65분)", 2220, "베이 셔틀 고속선 + 포트라이너 (약 50분)"),
            new AirportAccess(Airport.KIX, City.NARA, 95,
                    2400, "공항 리무진 버스 (긴테쓰나라까지 약 80분)", 2400, "공항 리무진 버스"),
            new AirportAccess(Airport.UKB, City.KOBE, 30,
                    340, "포트라이너 (산노미야까지 약 18분)", 340, "포트라이너"),
            // 중부
            new AirportAccess(Airport.NGO, City.NAGOYA, 45,
                    980, "메이테쓰 특급 (메이테쓰나고야까지 약 35~40분)", 1430, "메이테쓰 뮤스카이 (약 28분)"),
            new AirportAccess(Airport.NGO, City.TAKAYAMA, 210,
                    4730, "메이테쓰 + 나고야 → 다카야마 고속버스 (웹 예약, 약 3시간 15분)",
                    7040, "메이테쓰 + JR 특급 히다 자유석 (약 3시간)"),
            new AirportAccess(Airport.KMQ, City.KANAZAWA, 50,
                    1400, "호쿠테쓰 리무진 버스 (가나자와역까지 약 40분)", 1400, "호쿠테쓰 리무진 버스"),
            // 히로시마·규슈
            new AirportAccess(Airport.HIJ, City.HIROSHIMA, 60,
                    1500, "리무진 버스 (히로시마역까지 약 50분)", 1500, "리무진 버스"),
            new AirportAccess(Airport.FUK, City.FUKUOKA, 20,
                    260, "지하철 (하카타 약 5분 · 덴진 약 11분)", 260, "지하철"),
            new AirportAccess(Airport.FUK, City.BEPPU, 160,
                    3250, "니시테쓰 고속버스 도요노쿠니호 (약 2시간 30분)", 3250, "니시테쓰 고속버스"),
            new AirportAccess(Airport.FUK, City.YUFUIN, 140,
                    3250, "니시테쓰 고속버스 유후인호 (약 2시간 10~25분)", 3250, "니시테쓰 고속버스"),
            // 홋카이도·오키나와
            new AirportAccess(Airport.CTS, City.SAPPORO, 50,
                    1230, "JR 쾌속 에어포트 (삿포로까지 약 37분)", 2230, "에어포트 u시트 지정석 (+1,000)"),
            new AirportAccess(Airport.CTS, City.OTARU, 85,
                    2040, "JR 쾌속 에어포트 (오타루까지 약 73분)", 3040, "에어포트 u시트 지정석 (+1,000)"),
            new AirportAccess(Airport.OKA, City.NAHA, 25,
                    290, "유이레일 (겐초마에까지 약 12분)", 320, "유이레일 (마키시·오모로마치)"));

    /** 공항 + 도시 짝의 이동 정보. 값이 없는 짝이면 빈 값 */
    public static Optional<AirportAccess> of(Airport airport, City city) {
        return TABLE.stream().filter(a -> a.airport == airport && a.city == city).findFirst();
    }
}
