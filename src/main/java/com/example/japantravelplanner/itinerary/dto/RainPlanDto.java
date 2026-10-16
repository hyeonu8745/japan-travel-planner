package com.example.japantravelplanner.itinerary.dto;

import com.example.japantravelplanner.itinerary.planner.StopType;

import java.time.LocalTime;
import java.util.List;

/**
 * 비 오는 날 대안 미리보기 (10-16). 저장하지 않고 볼 때마다 계산한다. 적용은 POST로 새 버전(EDITED).
 *
 * @param version     이 미리보기를 계산한 일정 버전 (적용할 때 함께 보내면, 그 사이 바뀌었을 때 409)
 * @param changed     바꿀 곳이 있는지 (false면 적용 버튼을 숨긴다)
 * @param message     화면에 그대로 보여줄 한 줄
 * @param swaps       실외 → 실내로 바꾸는 곳
 * @param keptOutdoor 실외지만 근처에 바꿀 실내 장소가 없어 그대로 둔 곳 (원래 이름)
 * @param warnings    바꾼 뒤 시간 안내 ("일정이 21:00을 넘겨 끝나요" 등)
 * @param stops       바꾼 뒤 그날 순서와 시각 (식당은 그대로)
 */
public record RainPlanDto(Long tripId, int version, int dayNumber, boolean changed, String message,
                          List<SwapDto> swaps, List<String> keptOutdoor, List<String> warnings, List<PreviewStopDto> stops) {

    public record SwapDto(Long stopId, int order, PlaceDto from, PlaceDto to) {
    }

    public record PlaceDto(Long id, String name, String nameKo, List<String> labels, Double lat, Double lng, int stayMinutes) {
    }

    public record PreviewStopDto(int order, StopType type, String name, String nameKo,
                                 LocalTime arrival, LocalTime departure, boolean swapped) {
    }

    /** 적용 요청 (본문 없어도 됨) */
    public record ApplyRequest(Integer version) {
    }
}
