package com.apr.aprbackendassignment.ratelimit;

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
    void overflowKeepsExistingUserLimitedAndRejectsNewUser() {
        RedissonClient client = mock(RedissonClient.class);
        when(client.getRateLimiter(anyString())).thenThrow(new RedisException("Redis unavailable"));
        RateLimitProperties properties = properties();
        AtomicLong now = new AtomicLong();
        RedisRateLimiter limiter = new RedisRateLimiter(client, properties,
                now::get, TimeUnit.SECONDS.toNanos(2), 10_000);

        assertThat(limiter.allow(1L)).isTrue();
        assertThat(limiter.allow(1L)).isTrue();
        assertThat(limiter.allow(1L)).isFalse();
        for (long fakeId = 100_000; fakeId < 109_999; fakeId++) {
            assertThat(limiter.allow(fakeId)).as("fake user %s admitted to local window", fakeId).isTrue();
        }
        assertThat(limiter.allow(2L)).as("new user is rejected when no local slot remains").isFalse();
        assertThat(limiter.allow(1L)).as("existing user's limit remains enforced").isFalse();
        assertThat(limiter.allow(2L)).as("repeated overflow cannot bypass the limit").isFalse();
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
            assertThat(limiter.allow(overflowId)).isFalse();
        }
        assertThat(limiter.expiredSweepCount()).isEqualTo(1);
        verify(client, times(1)).getRateLimiter(anyString());

        now.set(TimeUnit.SECONDS.toNanos(2));
        for (long overflowId = 200_000; overflowId < 201_000; overflowId++) {
            assertThat(limiter.allow(overflowId)).isFalse();
        }
        assertThat(limiter.expiredSweepCount()).as("active windows need no repeated full sweep").isEqualTo(1);
        verify(client, times(2)).getRateLimiter(anyString());
        assertThat(limiter.allow(1L)).isTrue();
        assertThat(limiter.allow(1L)).isFalse();

        now.set(TimeUnit.SECONDS.toNanos(60));
        assertThat(limiter.allow(3L)).as("expired slots are reclaimed for a new user").isTrue();
        assertThat(limiter.expiredSweepCount()).isEqualTo(2);
        assertThat(limiter.allow(3L)).isTrue();
        assertThat(limiter.allow(3L)).isFalse();
    }

    @Test
    void fullLocalCapacityReclaimsExpiredSlotAtWindowBoundary() {
        RedissonClient client = mock(RedissonClient.class);
        when(client.getRateLimiter(anyString())).thenThrow(new RedisException("Redis unavailable"));
        RateLimitProperties properties = properties();
        properties.setWindowMilliSeconds(1_000);
        AtomicLong now = new AtomicLong();
        RedisRateLimiter limiter = new RedisRateLimiter(client, properties,
                now::get, TimeUnit.SECONDS.toNanos(2), 1);

        assertThat(limiter.allow(1L)).isTrue();
        assertThat(limiter.allow(2L)).as("capacity is full before expiry").isFalse();
        now.set(TimeUnit.MILLISECONDS.toNanos(999));
        assertThat(limiter.allow(2L)).as("original window remains active").isFalse();
        now.set(TimeUnit.SECONDS.toNanos(1));
        assertThat(limiter.allow(2L)).as("expired slot is available at the boundary").isTrue();
        assertThat(limiter.allow(1L)).as("newly admitted window retains the only slot").isFalse();
    }

    @Test
    void fullLocalCapacityReclaimsEarliestStaggeredWindow() {
        RedissonClient client = mock(RedissonClient.class);
        when(client.getRateLimiter(anyString())).thenThrow(new RedisException("Redis unavailable"));
        RateLimitProperties properties = properties();
        properties.setWindowMilliSeconds(1_000);
        AtomicLong now = new AtomicLong();
        RedisRateLimiter limiter = new RedisRateLimiter(client, properties,
                now::get, TimeUnit.SECONDS.toNanos(2), 2);

        assertThat(limiter.allow(1L)).isTrue();
        now.set(TimeUnit.MILLISECONDS.toNanos(900));
        assertThat(limiter.allow(2L)).isTrue();
        assertThat(limiter.allow(3L)).as("both local windows are active").isFalse();
        now.set(TimeUnit.MILLISECONDS.toNanos(999));
        assertThat(limiter.allow(3L)).as("oldest window has not expired").isFalse();
        now.set(TimeUnit.SECONDS.toNanos(1));
        assertThat(limiter.allow(3L)).as("oldest slot is reclaimed at its own expiry").isTrue();
        assertThat(limiter.allow(2L)).as("later window remains active").isTrue();
        assertThat(limiter.allow(2L)).isFalse();
        assertThat(limiter.allow(1L)).as("the only reclaimed slot now belongs to user 3").isFalse();
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

    private RateLimitProperties properties() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setMaxRequests(2);
        properties.setWindowMilliSeconds(60_000);
        return properties;
    }
}
