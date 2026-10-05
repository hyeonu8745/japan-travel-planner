package com.example.japantravelplanner.itinerary.planner;

import com.example.japantravelplanner.itinerary.planner.DayScheduler.RetimeItem;
import com.example.japantravelplanner.itinerary.planner.DayScheduler.Retimed;
import com.example.japantravelplanner.itinerary.travel.LegMode;
import com.example.japantravelplanner.itinerary.travel.TravelPoint;
import com.example.japantravelplanner.itinerary.travel.TravelProperties;
import com.example.japantravelplanner.itinerary.travel.TravelTimeService;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 식당을 바꿀 때처럼, 순서는 그대로 두고 시각만 다시 계산하는 규칙 */
class DaySchedulerRetimeTest {

    private static final TravelPoint HOTEL = new TravelPoint("숙소", 35.6951, 139.7020);
    private static final LocalTime START = LocalTime.of(9, 30);
    private static final LocalTime END = LocalTime.of(22, 0);

    private final DayScheduler scheduler = new DayScheduler(new TravelTimeService(new TravelProperties(), null));
    private final TravelOptions transit = new TravelOptions(LegMode.TRANSIT, 20);

    // 서로 가까운 신주쿠 안의 곳들
    private static final TravelPoint SPOT_A = new TravelPoint("관광지A", 35.6926, 139.7008);
    private static final TravelPoint SPOT_B = new TravelPoint("관광지B", 35.6938, 139.7046);
    private static final TravelPoint LUNCH_NEAR = new TravelPoint("가까운 점심집", 35.6935, 139.7030);
    private static final TravelPoint LUNCH_FAR = new TravelPoint("먼 점심집", 35.6600, 139.7292);   // 롯폰기 쪽, 약 5km
    private static final TravelPoint DINNER = new TravelPoint("저녁집", 35.6900, 139.6960);

    private List<RetimeItem> day(TravelPoint lunch) {
        return List.of(
                new RetimeItem(StopType.ATTRACTION, SPOT_A, 40),
                new RetimeItem(StopType.LUNCH, lunch, 60),
                new RetimeItem(StopType.ATTRACTION, SPOT_B, 40),
                new RetimeItem(StopType.DINNER, DINNER, 90));
    }

    @Test
    void 입력한_순서와_개수_그대로_시각을_돌려준다() {
        Retimed retimed = scheduler.retime(day(LUNCH_NEAR), START, END, HOTEL, transit);

        assertThat(retimed.stops()).hasSize(4);
        assertThat(retimed.stops().get(0).arrival()).isBefore(retimed.stops().get(1).arrival());
        assertThat(retimed.stops().get(1).arrival()).isBefore(retimed.stops().get(2).arrival());
        assertThat(retimed.stops().get(2).arrival()).isBefore(retimed.stops().get(3).arrival());
    }

    @Test
    void 체류_시간은_그대로이고_도착_다음_출발_사이가_체류_시간이다() {
        Retimed retimed = scheduler.retime(day(LUNCH_NEAR), START, END, HOTEL, transit);

        // 관광지A는 40분 머문다
        assertThat(java.time.Duration.between(retimed.stops().get(0).arrival(), retimed.stops().get(0).departure())
                .toMinutes()).isEqualTo(40);
    }

    @Test
    void 점심은_시간대보다_일찍_도착하면_11시30분까지_기다린다() {
        Retimed retimed = scheduler.retime(day(LUNCH_NEAR), START, END, HOTEL, transit);

        assertThat(retimed.stops().get(1).arrival()).isEqualTo(LocalTime.of(11, 30));
        assertThat(retimed.stops().get(1).waitMinutes()).isPositive();
    }

    @Test
    void 먼_식당으로_바꾸면_이동이_늘고_뒤의_시각이_밀린다() {
        Retimed near = scheduler.retime(day(LUNCH_NEAR), START, END, HOTEL, transit);
        Retimed far = scheduler.retime(day(LUNCH_FAR), START, END, HOTEL, transit);

        // 식당까지 오는 이동이 길어지고, 식당에서 다음 장소로 가는 이동도 길어진다
        assertThat(far.stops().get(1).travelIn().minutes()).isGreaterThan(near.stops().get(1).travelIn().minutes());
        assertThat(far.stops().get(2).travelIn().minutes()).isGreaterThan(near.stops().get(2).travelIn().minutes());
        assertThat(far.stops().get(3).arrival()).isAfterOrEqualTo(near.stops().get(3).arrival());
    }

    @Test
    void 시간이_모자라도_항목을_빼지_않고_안내_문장으로만_알린다() {
        // 하루가 끝나는 시각을 일부러 너무 이르게 잡는다
        Retimed retimed = scheduler.retime(day(LUNCH_FAR), START, LocalTime.of(14, 0), HOTEL, transit);

        assertThat(retimed.stops()).hasSize(4);   // 줄이지 않는다
        assertThat(retimed.warnings()).anyMatch(w -> w.startsWith("일정이 14:00"));
    }

    @Test
    void 문제가_없으면_안내_문장이_없다() {
        Retimed retimed = scheduler.retime(day(LUNCH_NEAR), START, END, HOTEL, transit);

        assertThat(retimed.warnings()).isEmpty();
    }
}