package com.example.japantravelplanner.itinerary.budget;

import com.example.japantravelplanner.itinerary.fare.FareProperties;
import com.example.japantravelplanner.itinerary.fare.TransportEstimate;
import com.example.japantravelplanner.itinerary.fare.TransportEstimator;
import com.example.japantravelplanner.itinerary.travel.TravelProperties;
import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.entity.ItineraryDay;
import com.example.japantravelplanner.itinerary.entity.ItineraryStop;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.itinerary.repository.ItineraryRepository;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.entity.TripPreference;
import com.example.japantravelplanner.trip.repository.TripPreferenceRepository;
import com.example.japantravelplanner.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 일정의 식비 요약 (10-12). 일정에 들어간 식당의 끼니별 1인 예산 범위(타베로그)를 더하고 인원을 곱해,
 * 그 일정 버전의 환율로 원화로 바꾼 뒤 식비 예산(전체 예산 × 식비 비율)과 비교한다. 저장하지 않고 볼 때마다 계산한다.
 */
@Service
@RequiredArgsConstructor
public class BudgetSummaryService {

    static final double DEFAULT_FOOD_RATIO = 0.4;

    private final TripRepository tripRepository;
    private final TripPreferenceRepository preferenceRepository;
    private final ItineraryRepository itineraryRepository;
    private final FareProperties fareProperties;
    private final TravelProperties travelProperties;

    @Transactional(readOnly = true)
    public BudgetSummaryDto summarize(Long userId, Long tripId) {
        Trip trip = tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new NotFoundException("여행을 찾을 수 없습니다."));
        Itinerary itinerary = itineraryRepository.findFirstByTripIdOrderByVersionNoDesc(tripId)
                .orElseThrow(() -> new NotFoundException("아직 만들어진 일정이 없습니다."));
        double foodRatio = preferenceRepository.findByTripId(tripId).map(TripPreference::getFoodBudgetRatio)
                .orElse(DEFAULT_FOOD_RATIO);
        return summarize(trip, itinerary, foodRatio, fareProperties, travelProperties);
    }

    static BudgetSummaryDto summarize(Trip trip, Itinerary itinerary, double foodRatio) {
        return summarize(trip, itinerary, foodRatio, new FareProperties(), new TravelProperties());
    }

    static BudgetSummaryDto summarize(Trip trip, Itinerary itinerary, double foodRatio,
                                      FareProperties fares, TravelProperties travel) {
        int travelers = trip.getTravelers() == null || trip.getTravelers() < 1 ? 1 : trip.getTravelers();
        double rate = itinerary.getKrwPerYen();
        int tripBudget = trip.getBudget() == null ? 0 : trip.getBudget();
        int foodBudget = (int) Math.round(tripBudget * foodRatio);

        List<BudgetSummaryDto.DayDto> days = new ArrayList<>();
        int personMin = 0, personMax = 0, meals = 0, unknown = 0;
        for (ItineraryDay day : itinerary.getDays()) {
            List<BudgetSummaryDto.MealDto> dayMeals = new ArrayList<>();
            int dayMin = 0, dayMax = 0;
            for (ItineraryStop stop : day.getStops()) {
                if (stop.getType() == StopType.ATTRACTION || stop.getRestaurant() == null) continue;
                Restaurant r = stop.getRestaurant();
                boolean lunch = stop.getType() == StopType.LUNCH;
                Integer[] range = range(lunch ? r.getBudgetLunchMin() : r.getBudgetDinnerMin(),
                        lunch ? r.getBudgetLunchMax() : r.getBudgetDinnerMax());
                meals++;
                if (range == null) {
                    unknown++;
                } else {
                    dayMin += range[0];
                    dayMax += range[1];
                }
                dayMeals.add(new BudgetSummaryDto.MealDto(stop.getType(), r.getName(), r.getNameKo(),
                        lunch ? r.getBudgetLunch() : r.getBudgetDinner(),
                        range == null ? null : range[0], range == null ? null : range[1]));
            }
            personMin += dayMin;
            personMax += dayMax;
            days.add(new BudgetSummaryDto.DayDto(day.getDayNumber(), day.getDate(), dayMin * travelers, dayMax * travelers,
                    krw(dayMin * travelers, rate), krw(dayMax * travelers, rate), dayMeals));
        }

        BudgetSummaryDto.Amount perPerson = new BudgetSummaryDto.Amount(personMin, personMax,
                krw(personMin, rate), krw(personMax, rate), meals, unknown);
        BudgetSummaryDto.Amount food = new BudgetSummaryDto.Amount(personMin * travelers, personMax * travelers,
                krw(personMin * travelers, rate), krw(personMax * travelers, rate), meals, unknown);
        String verdict = meals == unknown ? "UNKNOWN"
                : food.maxKrw() <= foodBudget ? "WITHIN"
                : food.minKrw() > foodBudget ? "OVER" : "MAYBE_OVER";
        int share = tripBudget <= 0 ? 0 : (int) Math.round(food.maxKrw() * 100.0 / tripBudget);

        // 10-16 교통비: 숙소로 돌아갈 때 도보 기준(30분)보다 멀면 지하철 한 구간
        double walkLimit = travel.getWalkThresholdMinutes() * travel.getWalkMetersPerMinute() / travel.getWalkDetour();
        TransportEstimate transport = TransportEstimator.estimate(trip, itinerary, fares, rate, travelers,
                walkLimit, travel.getTransitDetour());
        int totalMin = food.minYen() + transport.total().minYen();
        int totalMax = food.maxYen() + transport.total().maxYen();
        BudgetSummaryDto.Total total = new BudgetSummaryDto.Total(totalMin, totalMax, krw(totalMin, rate), krw(totalMax, rate));

        return new BudgetSummaryDto(trip.getId(), itinerary.getVersionNo(), travelers, rate,
                itinerary.getExchangeRateDate(), sourceLabel(itinerary.getExchangeRateSource()), tripBudget, foodRatio,
                foodBudget, food, perPerson, verdict, share, BudgetSummaryDto.NOTE, days, transport, total);
    }

    /**
     * 타베로그 예산 범위 → [최소, 최대] (1인, 엔). "～￥999"처럼 한쪽만 있으면(최소가 0으로 저장된 경우 포함)
     * 그 값을 양쪽에 쓴다. 0엔부터로 잡으면 식비가 실제보다 너무 적게 나온다. 둘 다 없으면(점심 "-" 등) null
     */
    static Integer[] range(Integer min, Integer max) {
        if (min != null && min <= 0) min = null;
        if (max != null && max <= 0) max = null;
        if (min == null && max == null) return null;
        int low = min != null ? min : max;
        int high = max != null ? max : min;
        return new Integer[]{Math.min(low, high), Math.max(low, high)};
    }

    static int krw(int yen, double krwPerYen) {
        return (int) Math.round(yen * krwPerYen);
    }

    static String sourceLabel(String source) {
        if (source == null) return "고정 환율";
        return switch (source) {
            case "KOREAEXIM" -> "한국수출입은행";
            case "FRANKFURTER" -> "Frankfurter(유럽중앙은행 등)";
            default -> "고정 환율";
        };
    }
}
