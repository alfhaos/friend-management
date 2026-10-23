package com.apr.aprbackendassignment.util;

import org.junit.jupiter.api.Test;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class RedisRateLimiterFallbackTest {

    @Test
    void recoveryDoesNotResetAnActiveLocalUserWindow() {
        RedissonClient client = mock(RedissonClient.class);
        RRateLimiter redisLimiter = mock(RRateLimiter.class);
        AtomicInteger calls = new AtomicInteger();
        when(client.getRateLimiter(anyString())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                throw new RedisException("Redis unavailable");
            }
            return redisLimiter;
        });
        when(redisLimiter.tryAcquire(1)).thenReturn(true);
        AtomicLong now = new AtomicLong();
        RedisRateLimiter limiter = new RedisRateLimiter(client, properties(),
                now::get, TimeUnit.SECONDS.toNanos(2), 10);

        assertThat(limiter.allow(1L)).isTrue();
        assertThat(limiter.allow(1L)).isTrue();
        now.set(TimeUnit.SECONDS.toNanos(2));
        assertThat(limiter.allow(2L)).as("another user probes Redis recovery").isTrue();
        assertThat(limiter.allow(1L)).as("same user remains limited for the original window").isFalse();
        assertThat(calls.get()).isEqualTo(2);

        now.set(TimeUnit.SECONDS.toNanos(60));
        assertThat(limiter.allow(1L)).as("expired local window permits Redis again").isTrue();
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void overflowKeepsExistingUserLimitedAndLetsNewUserReachDatabase() {
        RedissonClient client = mock(RedissonClient.class);
        when(client.getRateLimiter(anyString())).thenThrow(new RedisException("Redis unavailable"));
        LimitProperties properties = properties();
        AtomicLong now = new AtomicLong();
        RedisRateLimiter limiter = new RedisRateLimiter(client, properties,
                now::get, TimeUnit.SECONDS.toNanos(2), 10_000);

        assertThat(limiter.allow(1L)).isTrue();
        assertThat(limiter.allow(1L)).isTrue();
        assertThat(limiter.allow(1L)).isFalse();
        for (long fakeId = 100_000; fakeId < 109_999; fakeId++) {
            assertThat(limiter.allow(fakeId)).as("fake user %s admitted to local window", fakeId).isTrue();
        }
        assertThat(limiter.allow(2L)).as("new user reaches database without a local slot").isTrue();
        assertThat(limiter.allow(1L)).as("existing user's limit remains enforced").isFalse();
        assertThat(limiter.allow(2L)).as("overflow user remains untracked locally").isTrue();
        verify(client, times(1)).getRateLimiter(anyString());
    }

    @Test
    void repeatedOverflowIdsDoNotRescanAllLocalUsersOnEveryRequest() {
        RedissonClient client = mock(RedissonClient.class);
        when(client.getRateLimiter(anyString())).thenThrow(new RedisException("Redis unavailable"));
        AtomicLong now = new AtomicLong();
        RedisRateLimiter limiter = new RedisRateLimiter(client, properties(),
                now::get, TimeUnit.SECONDS.toNanos(2), 2);

        assertThat(limiter.allow(1L)).isTrue();
        assertThat(limiter.allow(2L)).isTrue();
        for (long overflowId = 100_000; overflowId < 101_000; overflowId++) {
            assertThat(limiter.allow(overflowId)).isTrue();
        }
        assertThat(limiter.expiredSweepCount()).isEqualTo(1);
        verify(client, times(1)).getRateLimiter(anyString());

        now.set(TimeUnit.SECONDS.toNanos(2));
        for (long overflowId = 200_000; overflowId < 201_000; overflowId++) {
            assertThat(limiter.allow(overflowId)).isTrue();
        }
        assertThat(limiter.expiredSweepCount()).isEqualTo(2);
        verify(client, times(2)).getRateLimiter(anyString());
        assertThat(limiter.allow(1L)).isTrue();
        assertThat(limiter.allow(1L)).isFalse();
    }

    @Test
    void openCircuitSkipsRedisAndAllowsOnlyOneRecoveryProbe() throws Exception {
        RedissonClient client = mock(RedissonClient.class);
        RRateLimiter redisLimiter = mock(RRateLimiter.class);
        when(redisLimiter.tryAcquire(1)).thenReturn(true);
        AtomicLong now = new AtomicLong();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch probeEntered = new CountDownLatch(1);
        CountDownLatch releaseProbe = new CountDownLatch(1);
        when(client.getRateLimiter(anyString())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                throw new RedisException("Redis unavailable");
            }
            probeEntered.countDown();
            if (!releaseProbe.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Probe release timed out");
            }
            return redisLimiter;
        });
        RedisRateLimiter limiter = new RedisRateLimiter(client, properties(),
                now::get, TimeUnit.SECONDS.toNanos(2), 10);

        assertThat(limiter.allow(1L)).isTrue();
        assertThat(limiter.allow(1L)).isTrue();
        assertThat(limiter.allow(1L)).isFalse();
        assertThat(calls.get()).isEqualTo(1);

        now.set(TimeUnit.SECONDS.toNanos(2));
        var executor = Executors.newSingleThreadExecutor();
        try {
            var probe = executor.submit(() -> limiter.allow(2L));
            assertThat(probeEntered.await(10, TimeUnit.SECONDS)).as("recovery probe started").isTrue();
            assertThat(limiter.allow(3L)).as("parallel request uses local limit during probe").isTrue();
            assertThat(calls.get()).as("only one Redis recovery probe").isEqualTo(2);
            releaseProbe.countDown();
            assertThat(probe.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(limiter.allow(4L)).as("Redis used again after recovery").isTrue();
            assertThat(calls.get()).isEqualTo(3);
        } finally {
            releaseProbe.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).as("probe worker terminated").isTrue();
        }
    }

    private LimitProperties properties() {
        LimitProperties properties = new LimitProperties();
        properties.setMaxRequests(2);
        properties.setWindowMilliSeconds(60_000);
        return properties;
    }
}
