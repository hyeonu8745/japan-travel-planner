package com.example.japantravelplanner.itinerary.dto;

import com.example.japantravelplanner.itinerary.dto.AttractionCandidatesResponseDto.CandidateDto;

import java.util.List;

/**
 * 하루 일정의 정해진 자리에 끼워 넣을 수 있는 관광지 후보.
 * 기준은 관광지 교체 후보와 같다 (일정에 안 쓴 곳, 같은 지역 먼저, 점수 높은 순, 같은 지역이 3곳 미만이면 3km 안의 다른 지역).
 * 후보 항목 형식도 교체 후보(CandidateDto)와 같다. shiftMinutes는 넣었을 때 그날이 끝나는 시각이 늦어지는 분이다.
 *
 * @param dayNumber 몇째 날
 * @param position  넣을 자리 (0이면 맨 앞)
 * @param broadened 같은 지역 후보가 부족해서 다른 지역 관광지가 섞였는지
 */
public record AddAttractionCandidatesResponseDto(
        int dayNumber,
        int position,
        boolean broadened,
        List<CandidateDto> candidates
) {
}
