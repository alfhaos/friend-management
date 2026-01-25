package com.apr.aprbackendassignment.util;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
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
@RequiredArgsConstructor
public class RedisRateLimiter {

    private final RedissonClient redissonClient;
    private final LimitProperties properties;

    private static final String KEY_PREFIX = "rate_limit:user:";

    public synchronized boolean allow(Long userId) {

        String key = KEY_PREFIX + userId;

        // 1. RateLimiter 객체 가져오기
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);

        // 2. 제한 규칙 설정 (최초 1회 혹은 설정 변경 시에만 Redis에 반영됨)
        // OVERALL: 모든 서버 합산, MAX_REQUESTS 만큼, WINDOW 시간 동안 허용
        rateLimiter.trySetRate(
                RateType.OVERALL,
                properties.getMaxRequests(),
                properties.getWindowMilliSeconds(),
                RateIntervalUnit.MILLISECONDS
        );

        // 3. 토큰 획득 시도 (획득 가능하면 true, 아니면 즉시 false)
        return rateLimiter.tryAcquire(1);
    }
}
