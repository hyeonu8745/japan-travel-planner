package com.example.japantravelplanner.itinerary.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationLockTest {

    private final GenerationLock lock = new GenerationLock();

    @Test
    void 같은_여행은_한_번에_하나만_만들_수_있다() {
        assertThat(lock.tryAcquire(5L)).isTrue();
        assertThat(lock.tryAcquire(5L)).isFalse();
    }

    @Test
    void 다른_여행은_동시에_만들_수_있다() {
        assertThat(lock.tryAcquire(5L)).isTrue();
        assertThat(lock.tryAcquire(6L)).isTrue();
    }

    @Test
    void 풀면_다시_만들_수_있다() {
        lock.tryAcquire(5L);
        lock.release(5L);
        assertThat(lock.tryAcquire(5L)).isTrue();
    }
}