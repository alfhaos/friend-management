package com.apr.aprbackendassignment.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * =====================================================
 * Class Name   : InMemoryRateLimiter
 * Description  :
 *  - 메모리 기반의 간단한 속도 제한기 구현 클래스
 *  - 친구 신청 기능에서 초당 횟수 제한을 위해 추가
 *  - 사용자 ID별로 요청 횟수를 추적하고 제한

 * 주요 기능
 *  - allow 메서드: 특정 사용자 ID에 대해 요청 허용 여부를 판단
 * =====================================================
 */
@Component
@RequiredArgsConstructor
public class InMemoryRateLimiter {

    private final RateLimitProperties properties;

    // 동시성 안정를 위해 ConcurrentHashMap과 AtomicInteger 사용
    private final Map<Long, AtomicInteger> counter = new ConcurrentHashMap<>();
    private final Map<Long, Long> timeMap = new ConcurrentHashMap<>();

    public synchronized boolean allow(Long userId) {

        long now = System.currentTimeMillis();

        timeMap.putIfAbsent(userId, now);
        counter.putIfAbsent(userId, new AtomicInteger(0));

        long start = timeMap.get(userId);

        if (now - start > properties.getWindowMilliSeconds()) {
            timeMap.put(userId, now);
            counter.get(userId).set(0);
        }

        return counter.get(userId).incrementAndGet() <= properties.getMaxRequests();
    }
}
