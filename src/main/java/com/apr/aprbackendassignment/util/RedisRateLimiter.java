package com.apr.aprbackendassignment.util;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
/**
 * =====================================================
 * Class Name   : RedisRateLimiter
 * Description  :
 *  - Redis를 활용한 요청 속도 제한기
 *  - 친구 신청 기능에서 초당 횟수 제한을 위해 추가
 *  - 사용자 ID별로 요청 횟수를 추적하고 제한

 * 주요 기능
 *  - allow 메서드: 특정 사용자 ID에 대해 요청 허용 여부를 판단
 * =====================================================
 */
@Component
@Slf4j
public class RedisRateLimiter {

    private final RedissonClient redissonClient;
    private final LimitProperties properties;

    private static final String KEY_PREFIX = "rate_limit:user:";
    private static final int MAX_LOCAL_USERS = 10_000;
    private static final long RETRY_DELAY_NANOS = TimeUnit.SECONDS.toNanos(2);
    private final LongSupplier nanoClock;
    private final long retryDelayNanos;
    private final int maxLocalUsers;
    private final ConcurrentHashMap<Long, LocalWindow> localWindows = new ConcurrentHashMap<>();
    private final ReentrantLock localAdmissionLock = new ReentrantLock();
    private final AtomicLong redisRetryAfterNanos = new AtomicLong();
    private final AtomicBoolean redisProbeInProgress = new AtomicBoolean();
    private final AtomicLong expiredSweepCount = new AtomicLong();
    private long nextLocalCleanupNanos;
    private boolean localCleanupScheduled;

    @Autowired
    public RedisRateLimiter(RedissonClient redissonClient, LimitProperties properties) {
        this(redissonClient, properties, System::nanoTime, RETRY_DELAY_NANOS, MAX_LOCAL_USERS);
    }

    RedisRateLimiter(RedissonClient redissonClient, LimitProperties properties,
                     LongSupplier nanoClock, long retryDelayNanos, int maxLocalUsers) {
        if (retryDelayNanos <= 0 || maxLocalUsers <= 0) {
            throw new IllegalArgumentException("Retry delay and local user capacity must be positive");
        }
        this.redissonClient = redissonClient;
        this.properties = properties;
        this.nanoClock = nanoClock;
        this.retryDelayNanos = retryDelayNanos;
        this.maxLocalUsers = maxLocalUsers;
    }

    public boolean allow(Long userId) {
        long now = nanoClock.getAsLong();
        long retryAfter = redisRetryAfterNanos.get();
        if (retryAfter != 0) {
            if (now - retryAfter < 0 || !redisProbeInProgress.compareAndSet(false, true)) {
                return allowLocally(userId);
            }
            try {
                return allowThroughRedis(userId, true);
            } finally {
                redisProbeInProgress.set(false);
            }
        }
        return allowThroughRedis(userId, false);
    }

    private boolean allowThroughRedis(Long userId, boolean recoveryProbe) {
        try {
            RRateLimiter rateLimiter = redissonClient.getRateLimiter(KEY_PREFIX + userId);
            rateLimiter.trySetRate(
                    RateType.OVERALL,
                    properties.getMaxRequests(),
                    properties.getWindowMilliSeconds(),
                    RateIntervalUnit.MILLISECONDS
            );
            boolean allowed = rateLimiter.tryAcquire(1);
            if (recoveryProbe) {
                redisRetryAfterNanos.set(0);
                log.info("Redis rate limiter recovered");
            }
            return allowed;
        } catch (RedisException | RedisConnectionFailureException e) {
            long now = nanoClock.getAsLong();
            long previous = redisRetryAfterNanos.getAndSet(now + retryDelayNanos);
            if (previous == 0) {
                log.warn("Redis rate limiter unavailable; using bounded local rate limits");
            }
            return allowLocally(userId);
        }
    }

    private boolean allowLocally(Long userId) {
        int maxRequests = properties.getMaxRequests();
        int windowMillis = properties.getWindowMilliSeconds();
        if (maxRequests <= 0 || windowMillis <= 0) {
            return false;
        }

        long now = nanoClock.getAsLong();
        long windowNanos = TimeUnit.MILLISECONDS.toNanos(windowMillis);
        while (true) {
            if (!localWindows.containsKey(userId)) {
                Boolean admitted = admitLocalUser(userId, now, windowNanos);
                if (admitted != null) {
                    return admitted;
                }
            }
            AtomicBoolean processed = new AtomicBoolean();
            AtomicBoolean allowed = new AtomicBoolean();
            localWindows.computeIfPresent(userId, (key, window) -> {
                processed.set(true);
                if (now - window.startedNanos >= windowNanos) {
                    window.startedNanos = now;
                    window.requests = 0;
                }
                if (window.requests < maxRequests) {
                    window.requests++;
                    allowed.set(true);
                }
                return window;
            });
            if (processed.get()) {
                return allowed.get();
            }
        }
    }

    private Boolean admitLocalUser(Long userId, long now, long windowNanos) {
        localAdmissionLock.lock();
        try {
            if (localWindows.containsKey(userId)) {
                return null;
            }
            if (localWindows.size() >= maxLocalUsers) {
                if (!localCleanupScheduled || now - nextLocalCleanupNanos >= 0) {
                    evictExpiredWindows(now, windowNanos);
                    nextLocalCleanupNanos = now + retryDelayNanos;
                    localCleanupScheduled = true;
                }
                if (localWindows.size() >= maxLocalUsers) {
                    // An untracked ID may proceed to the database while active user windows stay intact.
                    return true;
                }
            }
            LocalWindow window = new LocalWindow(now);
            window.requests = 1;
            localWindows.put(userId, window);
            return true;
        } finally {
            localAdmissionLock.unlock();
        }
    }

    private void evictExpiredWindows(long now, long windowNanos) {
        expiredSweepCount.incrementAndGet();
        for (Long userId : localWindows.keySet()) {
            localWindows.computeIfPresent(userId, (key, window) ->
                    now - window.startedNanos >= windowNanos ? null : window);
        }
    }

    long expiredSweepCount() {
        return expiredSweepCount.get();
    }

    private static final class LocalWindow {
        private long startedNanos;
        private int requests;

        private LocalWindow(long startedNanos) {
            this.startedNanos = startedNanos;
        }
    }
}
