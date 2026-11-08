package com.example.japantravelplanner.trip.service;

import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.repository.TripRepository;

/**
 * 내 여행 찾기. 다른 사람 여행이거나 없으면 404 (여행이 있는지도 알려주지 않는다).
 * 11-08 리팩터링: 서비스 15곳에 복사돼 있던 "findByIdAndUserId + 404"를 한 곳으로.
 * 저장소를 인자로 받는 정적 메서드라, 저장소를 목으로 쓰는 기존 테스트가 그대로 돈다.
 */
public final class TripAccess {

    public static final String NOT_FOUND_MESSAGE = "여행을 찾을 수 없습니다.";

    private TripAccess() {
    }

    public static Trip owned(TripRepository tripRepository, Long userId, Long tripId) {
        return tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND_MESSAGE));
    }
}
