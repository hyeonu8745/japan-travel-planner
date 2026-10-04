package com.example.japantravelplanner.itinerary.service;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 같은 여행의 일정을 동시에 두 번 만들지 못하게 한다. (더블 클릭, 새로고침 중복 요청)
 * 막지 않으면 AI(Terra) 호출 비용이 두 번 나가고, 같은 버전 번호로 저장하다 충돌한다.
 * 서버가 한 대인 동안만 유효하다. 서버를 늘리면 Redis 잠금으로 바꾼다 (#34).
 */
@Component
public class GenerationLock {

    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    /** @return 잠금을 얻었으면 true. 이미 만드는 중이면 false */
    public boolean tryAcquire(Long tripId) {
        return running.add(tripId);
    }

    public void release(Long tripId) {
        running.remove(tripId);
    }
}