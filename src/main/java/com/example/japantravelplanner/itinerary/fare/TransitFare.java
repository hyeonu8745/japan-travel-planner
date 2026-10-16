package com.example.japantravelplanner.itinerary.fare;

import java.util.ArrayList;
import java.util.List;

/**
 * 거리별 지하철 운임과 1일권 계산 (10-16). 상태 없는 계산만 둔다 (테스트하기 쉽게).
 */
public final class TransitFare {

    private TransitFare() {
    }

    /** 한 구간 요금. 거리는 km 올림(운영사 규칙과 같음), 표보다 길면 마지막 구간 요금 */
    public static int fare(List<FareProperties.Band> bands, int distanceMeters) {
        int km = Math.max(1, (int) Math.ceil(distanceMeters / 1000.0));
        for (FareProperties.Band band : bands) {
            if (km <= band.getMaxKm()) return band.getYen();
        }
        return bands.get(bands.size() - 1).getYen();
    }

    /**
     * 1일권을 언제 쓰면 가장 싼지 (날짜별 지하철 요금, 1인). 24시간권은 하루, 48시간권은 이어진 이틀, 72시간권은 이어진 사흘을 덮는다.
     * 실제로는 처음 쓴 시각부터 시간 단위지만, 하루 일정 단위로 근사한다.
     * 나머지 날은 그날 요금을 낸다. 1일권을 하나도 안 쓰는 게 가장 싸면 빈 목록.
     *
     * @param dayFares 날짜 순서대로 그날 지하철 요금 (보수적으로 최대값을 넣는다)
     */
    public static PassPlan bestPasses(List<Integer> dayFares, FareProperties props) {
        int n = dayFares.size();
        int[] best = new int[n + 1];          // best[i] = i번째 날부터 끝까지 최소 비용
        int[] choice = new int[n + 1];        // 0 = 그날 요금, 1·2·3 = 그날부터 n일권
        for (int i = n - 1; i >= 0; i--) {
            best[i] = dayFares.get(i) + best[i + 1];
            choice[i] = 0;
            int[] lengths = {1, 2, 3};
            int[] prices = {props.getPass24Yen(), props.getPass48Yen(), props.getPass72Yen()};
            for (int k = 0; k < 3; k++) {
                int len = lengths[k];
                if (i + len > n) continue;
                // 지하철을 안 타는 날까지 1일권으로 덮을 이유는 없다 (그날 요금 0이면 건너뛴다)
                boolean allRide = true;
                for (int d = i; d < i + len; d++) if (dayFares.get(d) == 0) allRide = false;
                if (!allRide) continue;
                int cost = prices[k] + best[i + len];
                if (cost < best[i]) {
                    best[i] = cost;
                    choice[i] = len;
                }
            }
        }
        List<Pass> passes = new ArrayList<>();
        int i = 0;
        while (i < n) {
            int len = choice[i];
            if (len == 0) {
                i++;
                continue;
            }
            int price = len == 1 ? props.getPass24Yen() : len == 2 ? props.getPass48Yen() : props.getPass72Yen();
            passes.add(new Pass(i + 1, i + len, len * 24, price));
            i += len;
        }
        int payAsYouGo = dayFares.stream().mapToInt(Integer::intValue).sum();
        return new PassPlan(best[0], payAsYouGo, List.copyOf(passes));
    }

    /** @param fromDay·toDay 1부터 (그 일정의 몇째 날), hours 24·48·72 */
    public record Pass(int fromDay, int toDay, int hours, int yen) {
    }

    /** @param cost 1일권을 섞어 쓸 때 최소 비용, payAsYouGo 그냥 탈 때 */
    public record PassPlan(int cost, int payAsYouGo, List<Pass> passes) {
        public int savings() {
            return payAsYouGo - cost;
        }
    }
}
