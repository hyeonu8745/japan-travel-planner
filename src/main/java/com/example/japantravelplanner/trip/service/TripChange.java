package com.example.japantravelplanner.trip.service;

import com.example.japantravelplanner.trip.dto.TripRequestDto;
import com.example.japantravelplanner.trip.entity.Interest;
import com.example.japantravelplanner.trip.entity.Trip;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 여행 조건을 고칠 때 무엇이 바뀌었고, 그래서 일정을 어떻게 맞춰야 하는지 판단한다.
 *
 * - REGENERATE: 날짜·인원·예산·여행 속도·관심사·알레르기 → 장소나 식당 자체가 달라지므로 일정을 새로 만든다 (AI 사용)
 * - RETIME: 숙소 주소·이동수단·이동 제약 → 장소는 그대로 두고 이동 시간과 시각만 다시 계산한다 (AI 없음)
 * - NONE: 숙소 이름만 바뀌었거나 바뀐 것이 없음 → 일정은 그대로
 *
 * @param fields 바뀐 항목의 요청 필드 이름 (화면에서 강조할 때 쓴다)
 */
public record TripChange(Impact impact, List<String> fields) {

    public enum Impact { NONE, RETIME, REGENERATE }

    /** 일정을 새로 만들어야 하는 항목 */
    private static final Set<String> REGENERATE_FIELDS = Set.of(
            "startDate", "endDate", "travelers", "budget", "travelPace", "interests", "allergies");

    /** 시각만 다시 계산하면 되는 항목 */
    private static final Set<String> RETIME_FIELDS = Set.of(
            "accommodationAddress", "transportMode", "mobilityConstraints");

    public static TripChange between(Trip trip, TripRequestDto request) {
        List<String> fields = new ArrayList<>();
        check(fields, "startDate", trip.getStartDate(), request.getStartDate());
        check(fields, "endDate", trip.getEndDate(), request.getEndDate());
        check(fields, "travelers", trip.getTravelers(), request.getTravelers());
        check(fields, "budget", trip.getBudget(), request.getBudget());
        check(fields, "travelPace", trip.getTravelPace(), request.getTravelPace());
        check(fields, "interests", interestSet(trip.getInterests()), interestSet(request.getInterests()));
        check(fields, "allergies", text(trip.getAllergies()), text(request.getAllergies()));
        check(fields, "mobilityConstraints", text(trip.getMobilityConstraints()), text(request.getMobilityConstraints()));
        check(fields, "transportMode", trip.getTransportMode(), request.getTransportMode());
        check(fields, "accommodationName", text(trip.getAccommodationName()), text(request.getAccommodationName()));
        check(fields, "accommodationAddress", text(trip.getAccommodationAddress()), text(request.getAccommodationAddress()));

        Impact impact = Impact.NONE;
        if (fields.stream().anyMatch(REGENERATE_FIELDS::contains)) {
            impact = Impact.REGENERATE;
        } else if (fields.stream().anyMatch(RETIME_FIELDS::contains)) {
            impact = Impact.RETIME;
        }
        return new TripChange(impact, List.copyOf(fields));
    }

    public boolean changed(String field) {
        return fields.contains(field);
    }

    /** 변경 이력에 남길 한국어 항목 이름: "숙소 주소, 이동수단" */
    public String labels() {
        return String.join(", ", fields.stream().map(TripChange::label).toList());
    }

    static String label(String field) {
        return switch (field) {
            case "startDate" -> "시작일";
            case "endDate" -> "종료일";
            case "travelers" -> "인원";
            case "budget" -> "예산";
            case "travelPace" -> "여행 속도";
            case "interests" -> "관심사";
            case "allergies" -> "알레르기";
            case "mobilityConstraints" -> "이동 제약";
            case "transportMode" -> "이동수단";
            case "accommodationName" -> "숙소 이름";
            case "accommodationAddress" -> "숙소 주소";
            default -> field;
        };
    }

    private static void check(List<String> fields, String name, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            fields.add(name);
        }
    }

    // 관심사는 순서가 의미 없으므로 집합으로 비교한다
    private static Set<Interest> interestSet(List<Interest> interests) {
        return interests == null ? Set.of() : new HashSet<>(interests);
    }

    // 앞뒤 공백과 빈 문자열(= 입력 안 함)은 바뀐 것으로 보지 않는다
    static String text(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
