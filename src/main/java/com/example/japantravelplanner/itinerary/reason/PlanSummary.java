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

    /**
     * 여행의 첫 일정(버전 1)에만 붙이는 사용법·주의사항 (10-11 사용자 요청).
     * 다시 만들 때마다 요약이 쌓이므로 그 뒤에는 EDIT_HINT 한 줄만 붙인다
     */
    static final String GUIDE = """
            [이렇게 써 보세요]
            - 마음에 들면 [완성]을 눌러 일정을 확정해 주세요. 완성 전에도 아래처럼 바꿀 수 있어요.
            - "2일차 저녁을 스시로 바꿔줘", "3일차에 정원 하나 넣어줘", "닌자 카페 빼줘"처럼 말하면 바꿔 드려요.
            - 식당이나 관광지를 누르면 사진·영업시간·리뷰를 볼 수 있어요.

            [참고해 주세요]
            - 이동 시간은 거리로 계산한 예상치예요. 실제 열차 시간은 Google 지도 길찾기에서 확인해 주세요.
            - 정기 휴일은 표시하지만, 임시 휴업이나 공휴일은 반영되지 않을 수 있어요. 방문 전에 영업 여부를 확인해 주세요.
            - 식당 상세에서 한국인 리뷰 중 주의할 내용이 보이면 알려 드려요. 리뷰 몇 개를 바탕으로 한 것이라 참고만 해 주세요. 마음에 들지 않으면 바로 다른 곳으로 바꿀 수 있어요.
            - 예산은 기준 환율로 계산한 대략적인 금액이에요.""";

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
            if (!ctx.food().unfilteredAllergies().isEmpty()) {
                lines.add(String.join("·", ctx.food().unfilteredAllergies())
                        + "는 여러 요리에 들어가서 식당 종류로 거를 수 없어요. 주문할 때 꼭 확인해 주세요.");
            } else if (ctx.food().hasAllergies()) {
                lines.add("알레르기는 식당 종류로만 걸렀어요. 메뉴 원재료는 주문할 때 꼭 확인해 주세요.");
            }

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
        sb.append(itinerary.getVersionNo() == 1 ? "\n" + GUIDE : EDIT_HINT);
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
