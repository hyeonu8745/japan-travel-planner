package com.example.japantravelplanner.trip.dto;

import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * 여행 조건 수정 결과.
 *
 * @param trip            저장된 여행 조건 (상태 포함)
 * @param changedFields   바뀐 항목의 필드 이름 (예: ["startDate", "budget"]). 바뀐 것이 없으면 빈 배열
 * @param itineraryUpdate 일정을 어떻게 맞췄는지
 */
@JsonPropertyOrder({"trip", "changedFields", "itineraryUpdate"})
public record TripUpdateResponseDto(TripResponseDto trip, List<String> changedFields,
                                    ItineraryUpdate itineraryUpdate) {

    /**
     * @param result    NONE(일정 영향 없음) / NO_ITINERARY(아직 만든 일정 없음) / RETIMED(시각만 다시 계산) /
     *                  REGENERATED(일정을 새로 만듦) / FAILED(조건은 저장했지만 일정을 맞추지 못함)
     * @param message   화면에 보여줄 안내 한 문장
     * @param itinerary 새 버전이 생겼으면 그 전체 일정, 아니면 null
     */
    @JsonPropertyOrder({"result", "message", "itinerary"})
    public record ItineraryUpdate(Result result, String message, ItineraryResponseDto itinerary) {
    }

    public enum Result { NONE, NO_ITINERARY, RETIMED, REGENERATED, FAILED }
}
