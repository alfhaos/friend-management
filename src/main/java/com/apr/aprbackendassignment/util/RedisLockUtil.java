package com.apr.aprbackendassignment.util;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisLockUtil {

    private static final String PREFIX_LOCK_KEY = "lock:friendship:";
    private final RedissonClient redissonClient;

    public RLock getLock(Long senderId, Long receiverId) {
        // 1. 방향에 상관없이 동일한 키 생성 (작은 ID가 앞으로)
        String lockKey = PREFIX_LOCK_KEY +
                Math.min(senderId, receiverId) + ":" +
                Math.max(senderId, receiverId);

        return redissonClient.getLock(lockKey);
    }
}
