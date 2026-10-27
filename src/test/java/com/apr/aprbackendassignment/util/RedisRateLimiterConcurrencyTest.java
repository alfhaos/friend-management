package com.apr.aprbackendassignment.util;

import org.junit.jupiter.api.Test;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RedissonClient;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisRateLimiterConcurrencyTest {

    @Test
    void differentUsersEnterRedisRateLimitChecksConcurrently() throws Exception {
        RedissonClient client = mock(RedissonClient.class);
        RRateLimiter redisLimiter = mock(RRateLimiter.class);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch redisEntered = new CountDownLatch(2);
        CountDownLatch releaseRedis = new CountDownLatch(1);
        when(client.getRateLimiter(anyString())).thenAnswer(invocation -> {
            redisEntered.countDown();
            if (!releaseRedis.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Redis release timed out");
            }
            return redisLimiter;
        });
        when(redisLimiter.tryAcquire(1)).thenReturn(true);
        AtomicLong now = new AtomicLong();
        RedisRateLimiter limiter = new RedisRateLimiter(client, properties(),
                now::get, TimeUnit.SECONDS.toNanos(2), 10);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            var first = executor.submit(() -> allowAfterStart(limiter, 1L, ready, start));
            var second = executor.submit(() -> allowAfterStart(limiter, 2L, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).as("workers ready").isTrue();
            start.countDown();
            assertThat(redisEntered.await(10, TimeUnit.SECONDS))
                    .as("both user-specific Redis checks entered before either returned")
                    .isTrue();

            releaseRedis.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            start.countDown();
            releaseRedis.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).as("workers terminated").isTrue();
        }
    }

    private boolean allowAfterStart(RedisRateLimiter limiter, Long userId, CountDownLatch ready,
                                    CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Start signal timed out");
        }
        return limiter.allow(userId);
    }

    private LimitProperties properties() {
        LimitProperties properties = new LimitProperties();
        properties.setMaxRequests(2);
        properties.setWindowMilliSeconds(60_000);
        return properties;
    }
}
