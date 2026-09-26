package com.sewlect.feed.support;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryFeedRefillGuardTest {

    private final InMemoryFeedRefillGuard guard = new InMemoryFeedRefillGuard();

    @Test
    void tryClaim_firstClaimForUser_succeeds() {
        assertThat(guard.tryClaim(UUID.randomUUID())).isTrue();
    }

    @Test
    void tryClaim_secondClaimForSameUserBeforeRelease_fails() {
        UUID userId = UUID.randomUUID();

        assertThat(guard.tryClaim(userId)).isTrue();
        assertThat(guard.tryClaim(userId)).isFalse();
    }

    @Test
    void tryClaim_afterRelease_succeedsAgain() {
        UUID userId = UUID.randomUUID();
        guard.tryClaim(userId);

        guard.release(userId);

        assertThat(guard.tryClaim(userId)).isTrue();
    }

    @Test
    void tryClaim_differentUsers_areIndependent() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        assertThat(guard.tryClaim(userA)).isTrue();
        assertThat(guard.tryClaim(userB)).isTrue();
        assertThat(guard.tryClaim(userA)).isFalse();
    }

    @Test
    void release_forUserNeverClaimed_isANoOpNotAnException() {
        assertThat(guard.tryClaim(UUID.randomUUID())).isTrue();
        UUID neverClaimed = UUID.randomUUID();

        guard.release(neverClaimed);

        assertThat(guard.tryClaim(neverClaimed)).isTrue();
    }

    @Test
    void release_calledTwice_isIdempotent() {
        UUID userId = UUID.randomUUID();
        guard.tryClaim(userId);

        guard.release(userId);
        guard.release(userId);

        assertThat(guard.tryClaim(userId)).isTrue();
    }

    @Test
    void tryClaim_underHeavyConcurrentContentionForSameUser_exactlyOneWinner() throws InterruptedException {
        UUID userId = UUID.randomUUID();
        int threadCount = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Boolean> results = new CopyOnWriteArrayList<>();

        try {
            for (int i = 0; i < threadCount; i++) {
                pool.submit(() -> {
                    try {
                        startGate.await();
                        results.add(guard.tryClaim(userId));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startGate.countDown();
            boolean completed = doneLatch.await(5, TimeUnit.SECONDS);

            assertThat(completed).isTrue();
            assertThat(results).hasSize(threadCount);
            assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void tryClaim_underHeavyConcurrentContentionForDifferentUsers_everyoneWins() throws InterruptedException {
        int threadCount = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Boolean> results = new CopyOnWriteArrayList<>();

        try {
            for (int i = 0; i < threadCount; i++) {
                UUID distinctUser = UUID.randomUUID();
                pool.submit(() -> {
                    results.add(guard.tryClaim(distinctUser));
                    doneLatch.countDown();
                });
            }

            boolean completed = doneLatch.await(5, TimeUnit.SECONDS);

            assertThat(completed).isTrue();
            assertThat(results).allMatch(Boolean::booleanValue);
        } finally {
            pool.shutdownNow();
        }
    }
}