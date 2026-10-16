package com.example.japantravelplanner.itinerary.fare;

import java.time.LocalDate;
import java.util.List;

/**
 * 교통비 추정 결과 (10-16, 식비 요약 응답의 transport). 금액은 엔, 원은 그 일정 버전의 환율로.
 *
 * @param perPerson  1인 교통비 (지하철 + 공항)
 * @param total      인원 전체
 * @param subway     지하철 (일정의 대중교통 구간 + 마지막 장소 → 숙소)
 * @param airport    공항 ↔ 시내 (여행 조건에 비행기 시각이 있을 때만, 없으면 null)
 * @param rentalCar  렌터카 여행이면 true (지하철 요금 없음, 렌터카 비용은 넣지 않음)
 * @param note       화면에 그대로 보여줄 안내
 */
public record TransportEstimate(Range perPerson, Range total, Subway subway, Airport airport, boolean rentalCar,
                                List<Day> days, String note) {

    public record Range(int minYen, int maxYen, int minKrw, int maxKrw) {
    }

    /**
     * @param legs       대중교통 구간 수
     * @param minYen     그냥 탈 때 최소 (도쿄 메트로 운임) / maxYen 최대 (도에이 운임)
     * @param passes     1일권을 쓰는 게 더 싸면 언제 어떤 권을 살지 (아니면 빈 목록)
     * @param passCost   1일권을 섞어 쓸 때 비용 (passes가 비어 있으면 maxYen과 같음)
     * @param savingsYen 1일권으로 아끼는 금액 (최대 요금 기준)
     * @param finalMinYen·finalMaxYen 1일권까지 고려한 지하철 비용 (요금과 1일권 중 싼 쪽)
     */
    public record Subway(int legs, int minYen, int maxYen, List<TransitFare.Pass> passes, int passCost,
                         int savingsYen, int finalMinYen, int finalMaxYen) {
    }

    public record Airport(Leg arrival, Leg departure, int minYen, int maxYen) {
    }

    /** 공항 한 번 (도착 또는 출발) */
    public record Leg(String airport, int cheapYen, String cheapLabel, int fastYen, String fastLabel) {
    }

    /** 하루 지하철 (1인). 1일권 적용 전 요금 */
    public record Day(int dayNumber, LocalDate date, int legs, int minYen, int maxYen) {
    }

    public static final String NOTE = "교통비는 지하철 거리별 운임(IC 카드)과 공항 철도·버스 요금으로 계산한 추정이에요. "
            + "갈아타거나 JR·사철 구간을 타면 더 나올 수 있어요. 택시·렌터카 비용은 넣지 않았어요.";
}
