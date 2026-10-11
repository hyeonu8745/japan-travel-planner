package com.example.japantravelplanner.itinerary.reason;

import com.example.japantravelplanner.itinerary.candidate.Wish;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.entity.ItineraryStop;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.trip.entity.Interest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 일정을 만든 뒤 채팅에 남기는 "이렇게 짰어요" 메시지.
 * AI가 아니라 서버가 저장된 일정과 여행 조건에서 사실만 모아 쓴다 (지어낸 이유가 섞이지 않게).
 * 관심사·좋아하는 음식이 반영되지 못한 경우도 숨기지 않고 알려준다.
 */
public final class PlanSummary {

    /** 관심사마다 이름을 보여줄 관광지 수 (나머지는 "외 N곳") */
    static final int MAX_NAMES = 2;
    /** 일정을 만든 뒤에는 채팅으로 고칠 수 있다고 알려준다 (10-11) */
    static final String EDIT_HINT = "바꾸고 싶은 곳이 있으면 \"2일차 저녁을 스시로 바꿔줘\"처럼 여기에 말씀해 주세요.";

    private PlanSummary() {
    }

    public static String write(Itinerary itinerary, ReasonContext context) {
        ReasonContext ctx = ReasonContext.orNone(context);
        List<ItineraryStop> stops = itinerary.getDays().stream().flatMap(day -> day.getStops().stream()).toList();
        List<String> lines = new ArrayList<>();

        // 관심사 (조건 입력에서 고른 순서대로)
        Map<Interest, Set<String>> byInterest = new LinkedHashMap<>();
        ctx.interests().forEach(interest -> byInterest.put(interest, new LinkedHashSet<>()));
        for (ItineraryStop stop : stops) {
            if (stop.getType() != StopType.ATTRACTION) continue;
            for (Interest interest : StopReasons.matchedInterests(stop.getAttraction(), ctx)) {
                byInterest.get(interest).add(displayName(stop));
            }
        }
        byInterest.forEach((interest, names) -> lines.add(names.isEmpty()
                ? "관심사 '" + StopReasons.interestLabel(interest) + "'에 맞는 곳은 이번 동선에 넣지 못했어요."
                : "관심사 '" + StopReasons.interestLabel(interest) + "'에 맞는 곳: " + names(names)));   // 이름 뒤 조사(을/를)는 받침에 따라 달라져서 쓰지 않는다

        // 대화에서 말한 취향 (관심사 체크박스에 없는 것)
        Map<Wish, Set<String>> byWish = new LinkedHashMap<>();
        ctx.wishes().stream().sorted().forEach(wish -> byWish.put(wish, new LinkedHashSet<>()));
        for (ItineraryStop stop : stops) {
            if (stop.getType() != StopType.ATTRACTION) continue;
            for (Wish wish : StopReasons.matchedWishes(stop.getAttraction(), ctx)) {
                byWish.get(wish).add(displayName(stop));
            }
        }
        byWish.forEach((wish, names) -> lines.add(names.isEmpty()
                ? "말씀하신 '" + wish.label() + "' 취향에 맞는 곳은 이번 동선에 넣지 못했어요."
                : "말씀하신 '" + wish.label() + "' 취향에 맞는 곳: " + names(names)));

        // 식당
        List<ItineraryStop> meals = stops.stream().filter(stop -> stop.getType() != StopType.ATTRACTION).toList();
        if (!meals.isEmpty()) {
            lines.add("식당은 동선 가까이에서 평점이 높은 곳으로 골랐어요.");
            if (!ctx.food().preferGenres().isEmpty()) {
                long preferred = meals.stream()
                        .filter(stop -> StopReasons.matchesPreferredFood(stop.getRestaurant(), ctx)).count();
                lines.add(preferred > 0
                        ? "좋아하는 음식은 " + preferred + "끼에 넣었어요."
                        : "좋아하는 음식은 동선 근처에서 찾지 못해 넣지 못했어요.");
            }
            if (!ctx.food().avoidGenres().isEmpty()) lines.add("피하고 싶은 음식이 들어간 식당은 뺐어요.");
            if (ctx.food().avoidSmoky()) lines.add("담배 연기가 있을 수 있는 이자카야·바는 뺐어요.");

            long relaxed = meals.stream().filter(ItineraryStop::isBudgetRelaxed).count();
            if (relaxed > 0) lines.add("예산 안에서 찾지 못해 예산을 조금 넘는 식당이 " + relaxed + "곳 있어요.");
            long repeated = meals.stream().filter(ItineraryStop::isGenreRepeated).count();
            if (repeated > 0) lines.add("앞 끼니와 같은 음식을 피하지 못한 식당이 " + repeated + "곳 있어요.");
        }

        int dropped = itinerary.getDays().stream().mapToInt(day -> day.getDropped().size()).sum();
        if (dropped > 0) lines.add("시간이 맞지 않아 일정에서 빠진 장소가 " + dropped + "곳 있어요. 날짜별 '빠진 장소'에서 볼 수 있어요.");

        StringBuilder sb = new StringBuilder("일정을 이렇게 짰어요.\n");
        lines.forEach(line -> sb.append("- ").append(line).append('\n'));
        sb.append("장소마다 고른 이유도 함께 표시해 두었어요.\n");
        sb.append(EDIT_HINT);
        return sb.toString();
    }

    /** 한국어 이름이 있으면 그것을 (화면 일정표와 같은 이름) */
    private static String displayName(ItineraryStop stop) {
        String nameKo = stop.getAttraction().getNameKo();
        return nameKo == null || nameKo.isBlank() ? stop.getAttraction().getName() : nameKo;
    }

    private static String names(Set<String> names) {
        List<String> list = List.copyOf(names);
        String shown = String.join(", ", list.subList(0, Math.min(MAX_NAMES, list.size())));
        return list.size() > MAX_NAMES ? shown + " 외 " + (list.size() - MAX_NAMES) + "곳" : shown;
    }
}
