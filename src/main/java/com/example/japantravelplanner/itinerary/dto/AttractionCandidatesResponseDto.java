package com.example.japantravelplanner.itinerary.dto;

import java.util.List;

/**
 * 관광지 항목 하나를 바꿀 때 고를 수 있는 후보.
 * 그날의 지역에 있고 일정에 아직 안 쓴 곳만, 점수 높은 순으로 준다 (일정을 만들 때와 같은 점수·리뷰 기준).
 *
 * 같은 지역에 남은 후보가 적으면(3곳 미만) 바꾸려는 장소에서 3km 안의 다른 지역 관광지도 함께 준다.
 * 같은 지역 후보가 먼저, 그다음 다른 지역 후보가 나온다.
 *
 * @param night     저녁 뒤 야경 장소인지. true면 후보는 야경 장소(전망대 + 대표 야경 명소)만 나온다
 * @param broadened 같은 지역 후보가 부족해서 다른 지역 관광지가 후보에 섞였는지
 */
public record AttractionCandidatesResponseDto(
        Long stopId,
        boolean night,
        boolean broadened,
        List<CandidateDto> candidates
) {

    /**
     * @param stayMinutes  이 장소에서 머무는 시간 (장소 종류와 여행 속도로 계산)
     * @param travel       앞 장소(첫 항목이면 숙소)에서 이 장소까지 오는 이동
     * @param shiftMinutes 이 장소로 바꾸면 그날 마지막 항목이 끝나는 시각이 이만큼 달라진다 (+ 늦어짐, - 빨라짐)
     * @param fitsInDay    바꿔도 하루 안에 들어오는지 (점심·저녁 시간대와 하루 끝 시각을 넘지 않는지)
     * @param sameZone     그날의 지역에 있는 장소인지 (false면 가까운 다른 지역의 장소)
     */
    public record CandidateDto(
            Long id,
            String name,
            List<String> labels,
            Double rating,
            Integer reviewCount,
            String address,
            String ward,
            String placeId,
            Double lat,
            Double lng,
            int stayMinutes,
            ItineraryResponseDto.TravelDto travel,
            int shiftMinutes,
            boolean fitsInDay,
            boolean sameZone,
            String nameKo               // 10-11 추가: 저장된 한국어 이름 (아직 없으면 null, 후보 목록에서는 새로 만들지 않는다)
    ) {
    }
}