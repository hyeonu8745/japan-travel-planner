package com.example.japantravelplanner.itinerary.planner;

/** 시간 계산에서 빠진 관광지 (화면에 "빠진 장소"로 안내) */
public record DroppedStop(String name, DropReason reason) {
}