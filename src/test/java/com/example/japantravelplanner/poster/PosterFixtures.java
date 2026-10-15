package com.example.japantravelplanner.poster;

import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto;
import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto.*;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.itinerary.travel.LegMode;
import com.example.japantravelplanner.itinerary.zone.TripZone;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

final class PosterFixtures {

    private PosterFixtures() {
    }

    static StopDto attraction(String time, String nameKo, String label, int stay, LegMode mode) {
        return new StopDto(1L, 1, StopType.ATTRACTION, "原名", LocalTime.parse(time), LocalTime.parse(time).plusMinutes(stay),
                stay, 0, new TravelDto(mode, 10, 800, true), 35.7, 139.7,
                new AttractionDto(1L, List.of(label), 4.5, 1000, "주소", "台東区", "pid"), null, List.of(), List.of(), nameKo);
    }

    static StopDto meal(StopType type, String time, String nameKo, String genre) {
        return new StopDto(2L, 2, type, "店", LocalTime.parse(time), LocalTime.parse(time).plusMinutes(60), 60, 0,
                new TravelDto(LegMode.WALK, 5, 300, true), 35.7, 139.7, null,
                new RestaurantDto(2L, "ラーメン", 3.6, "￥1,000～￥1,999", "주소", false, false, 100, "역", 100, null, false,
                        List.of(genre)),
                List.of(), List.of(), nameKo);
    }

    static DayDto day(int n, String zonesLabel, List<StopDto> stops) {
        return new DayDto(n, LocalDate.of(2026, 10, 24 + n), TripZone.ASAKUSA_UENO, "아사쿠사·우에노", LocalTime.of(9, 30),
                LocalTime.of(21, 0), null, stops, List.of(), List.of(), List.of(TripZone.ASAKUSA_UENO), zonesLabel, List.of());
    }

    static ItineraryResponseDto itinerary(int days) {
        List<DayDto> list = new ArrayList<>();
        for (int i = 1; i <= days; i++) {
            list.add(day(i, "아사쿠사·우에노 + 아키하바라·간다", List.of(
                    attraction("09:30", "센소지", "사찰", 50, LegMode.TRANSIT),
                    meal(StopType.LUNCH, "12:00", "라멘 이나리야", "라멘"),
                    attraction("13:30", "아메요코 상점가", "시장", 90, LegMode.WALK),
                    attraction("15:30", "우에노 공원", "공원", 75, LegMode.WALK),
                    meal(StopType.DINNER, "18:00", "덴푸라 다이코쿠야", "덴푸라"),
                    attraction("20:00", "도쿄 스카이트리", "전망대", 60, LegMode.TRANSIT))));
        }
        return new ItineraryResponseDto(1L, 9L, 3, null, null, 9.0, LocalDateTime.now(), null, list, null);
    }
}
