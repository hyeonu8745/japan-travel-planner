package com.example.japantravelplanner.itinerary.dto;

import com.example.japantravelplanner.trip.entity.TripStatus;

/**
 * 일정 [완성] 확인 응답. POST /api/trips/{tripId}/itinerary/confirm
 *
 * @param tripId    여행 id
 * @param status    확인 뒤 여행 상태 (보통 GENERATED. 이미 고친 뒤면 EDITING 그대로)
 * @param itinerary 가장 최근 버전의 일정 (GET /itinerary와 같은 형식)
 */
public record ItineraryConfirmResponseDto(Long tripId, TripStatus status, ItineraryResponseDto itinerary) {

    /** 한국어 이름을 채운 일정으로 바꾼 응답 */
    public ItineraryConfirmResponseDto withItinerary(ItineraryResponseDto filled) {
        return new ItineraryConfirmResponseDto(tripId, status, filled);
    }
}
