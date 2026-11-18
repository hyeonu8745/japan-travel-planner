package com.example.japantravelplanner.chat.service;

import com.example.japantravelplanner.itinerary.reason.StopReasons;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.region.City;

import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

/**
 * 취향 대화의 첫 메시지. 사용자가 먼저 말하기 전에 서버가 인사와 첫 질문(음식)을 보낸다 (AI 호출 없음).
 * 첫 메시지가 없어서 사용자가 무엇을 말해야 할지 모른 채 대화가 시작됐다.
 */
final class ChatGreeting {

    static final String FIRST_QUESTION = "먼저, 여행 중에 꼭 드셔 보고 싶은 음식이나 피하고 싶은 음식이 있으세요?";

    /** 정적 메서드만 쓴다 */
    private ChatGreeting() {
    }

    /**
     * 여행 도시를 알 때만 (숙소 이름·주소에 도시 이름이 있거나, 이미 숙소 좌표로 정했을 때).
     * 모르면 도시 이름 없이 인사한다 (예전처럼 무조건 "도쿄"라고 하지 않게)
     */
    static java.util.Optional<City> cityOf(Trip trip) {
        java.util.Optional<City> guessed = City.guess(trip.getAccommodationName(), trip.getAccommodationAddress());
        if (guessed.isPresent()) return guessed;
        return trip.getAccommodationLocation() != null ? java.util.Optional.of(trip.getBaseCity()) : java.util.Optional.empty();
    }

    /** 여행 기간을 넣은 첫 인사와 첫 질문(음식) */
    static String of(Trip trip) {
        StringBuilder sb = new StringBuilder("안녕하세요! TabiMate예요. ");
        String place = cityOf(trip).map(c -> c.label() + " ").orElse("");
        if (trip.getStartDate() != null && trip.getEndDate() != null) {
            long nights = ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate());
            sb.append(nights).append("박 ").append(nights + 1).append("일 ").append(place).append("여행 일정을 함께 준비해 드릴게요.\n\n");
        } else {
            sb.append(place).append("여행 일정을 함께 준비해 드릴게요.\n\n");
        }

        if (trip.getInterests() != null && !trip.getInterests().isEmpty()) {
            String interests = trip.getInterests().stream()
                    .map(StopReasons::interestLabel).collect(Collectors.joining(", "));
            sb.append("알려주신 관심사(").append(interests)
                    .append(")를 바탕으로, 몇 가지만 더 여쭤보고 취향에 맞게 짜 드릴게요.\n\n");
        } else {
            sb.append("몇 가지만 여쭤보고 취향에 맞게 짜 드릴게요.\n\n");
        }
        return sb.append(FIRST_QUESTION).toString();
    }
}
