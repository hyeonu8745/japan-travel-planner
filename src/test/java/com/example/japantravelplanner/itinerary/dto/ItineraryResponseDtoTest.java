package com.example.japantravelplanner.itinerary.dto;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.planner.DropReason;
import com.example.japantravelplanner.itinerary.planner.DroppedStop;
import com.example.japantravelplanner.itinerary.planner.ScheduledDay;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.itinerary.service.ItineraryMapper;
import com.example.japantravelplanner.itinerary.travel.LegMode;
import com.example.japantravelplanner.itinerary.zone.TripZone;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ItineraryResponseDtoTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    private final Attraction cat = Attraction.builder().id(11L).name("新宿東口の猫").types("tourist_attraction")
            .rating(4.48).reviewCnt(1200).address("東京都新宿区")
            .location(GF.createPoint(new Coordinate(139.7008, 35.6926))).build();
    private final Restaurant ramen = Restaurant.builder().id(21L).name("つけ麺 五ノ神製作所").genre("つけ麺")
            .ratingVal(3.78).budgetLunch("～￥999").budgetDinner("￥3,000～￥3,999").address("東京都新宿区食堂")
            .location(GF.createPoint(new Coordinate(139.7100, 35.7000))).build();

    private ItineraryResponseDto dto() {
        ScheduledDay day = new ScheduledDay(1, LocalDate.of(2026, 10, 20), TripZone.SHINJUKU, List.of(
                Trips.stop(StopType.ATTRACTION, cat, ramen),
                Trips.stop(StopType.LUNCH, cat, ramen),
                Trips.stop(StopType.DINNER, cat, ramen)),
                List.of(new DroppedStop("ゴジラヘッド", DropReason.TIME_SHORTAGE)),
                List.of("안내 문장"));

        Itinerary itinerary = ItineraryMapper.toEntity(Trips.trip(), 2, 9.0, List.of(day),
                List.of(Trips.window()), Map.of(1, "하루 설명이에요."),
                id -> cat, id -> ramen);
        return ItineraryResponseDto.from(itinerary, types -> List.of("관광명소"));
    }

    @Test
    void 머리글과_하루_정보() {
        ItineraryResponseDto dto = dto();

        assertThat(dto.tripId()).isEqualTo(5L);
        assertThat(dto.version()).isEqualTo(2);
        assertThat(dto.krwPerYen()).isEqualTo(9.0);

        ItineraryResponseDto.DayDto day = dto.days().get(0);
        assertThat(day.dayNumber()).isEqualTo(1);
        assertThat(day.zone()).isEqualTo(TripZone.SHINJUKU);
        assertThat(day.zoneLabel()).isEqualTo("신주쿠");
        assertThat(day.description()).isEqualTo("하루 설명이에요.");
        assertThat(day.warnings()).containsExactly("안내 문장");
    }

    @Test
    void 빠진_장소는_이유_문장과_함께_나간다() {
        ItineraryResponseDto.DroppedDto dropped = dto().days().get(0).dropped().get(0);

        assertThat(dropped.name()).isEqualTo("ゴジラヘッド");
        assertThat(dropped.reason()).isEqualTo(DropReason.TIME_SHORTAGE);
        assertThat(dropped.reasonLabel()).isEqualTo("시간이 부족해서");
    }

    @Test
    void 관광지_항목은_좌표와_상세_정보를_담는다() {
        ItineraryResponseDto.StopDto stop = dto().days().get(0).stops().get(0);

        assertThat(stop.type()).isEqualTo(StopType.ATTRACTION);
        assertThat(stop.name()).isEqualTo("新宿東口の猫");
        // JTS 좌표는 (경도, 위도) 순서라서 x가 경도
        assertThat(stop.lat()).isEqualTo(35.6926);
        assertThat(stop.lng()).isEqualTo(139.7008);
        assertThat(stop.attraction().id()).isEqualTo(11L);
        assertThat(stop.attraction().labels()).containsExactly("관광명소");
        assertThat(stop.attraction().rating()).isEqualTo(4.48);
        assertThat(stop.attraction().reviewCount()).isEqualTo(1200);
        assertThat(stop.restaurant()).isNull();
    }

    @Test
    void 식당_항목은_끼니에_맞는_예산_문구를_담는다() {
        List<ItineraryResponseDto.StopDto> stops = dto().days().get(0).stops();

        ItineraryResponseDto.StopDto lunch = stops.get(1);
        ItineraryResponseDto.StopDto dinner = stops.get(2);

        assertThat(lunch.restaurant().budget()).isEqualTo("～￥999");
        assertThat(dinner.restaurant().budget()).isEqualTo("￥3,000～￥3,999");
        assertThat(dinner.restaurant().genre()).isEqualTo("つけ麺");
        assertThat(dinner.restaurant().id()).isEqualTo(21L);
        assertThat(dinner.attraction()).isNull();
        assertThat(dinner.lat()).isEqualTo(35.7000);
    }

    @Test
    void 이동과_시각_정보() {
        ItineraryResponseDto.StopDto stop = dto().days().get(0).stops().get(0);

        assertThat(stop.order()).isEqualTo(1);
        assertThat(stop.arrival()).isEqualTo(LocalTime.of(16, 15));
        assertThat(stop.travel().mode()).isEqualTo(LegMode.WALK);
        assertThat(stop.travel().minutes()).isEqualTo(10);
        assertThat(stop.travel().estimated()).isFalse();
    }
}