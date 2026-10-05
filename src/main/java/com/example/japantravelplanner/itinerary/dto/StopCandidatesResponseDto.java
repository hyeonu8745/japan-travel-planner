package com.example.japantravelplanner.itinerary.dto;

import com.example.japantravelplanner.itinerary.planner.StopType;

import java.util.List;

/**
 * 식당(점심·저녁) 항목 하나를 바꿀 때 고를 수 있는 후보.
 * 후보는 일정을 만들 때와 같은 규칙(예산, 취향·알레르기, 흡연)으로 걸러서 점수 높은 순으로 준다.
 *
 * @param budgetMaxYen 이 끼니의 1인 예산 상한 (엔)
 */
public record StopCandidatesResponseDto(
        Long stopId,
        StopType type,
        int budgetMaxYen,
        List<CandidateDto> candidates
) {

    /**
     * @param distanceFromAnchorMeters 후보를 찾은 기준 위치(앞뒤 장소의 중간)에서의 거리
     * @param travel                   앞 장소(첫 항목이면 숙소)에서 이 식당까지 오는 이동
     * @param genreRepeated            바로 앞 끼니와 같은 음식인지
     * @param budgetRelaxed            예산 안에서 못 찾아 상한을 올려서 찾은 식당인지
     */
    public record CandidateDto(
            Long id,
            String name,
            String genre,
            Double rating,
            Integer reviewCount,
            String budget,
            String address,
            String nearestStation,
            Double lat,
            Double lng,
            int distanceFromAnchorMeters,
            ItineraryResponseDto.TravelDto travel,
            boolean genreRepeated,
            boolean budgetRelaxed
    ) {
    }
}