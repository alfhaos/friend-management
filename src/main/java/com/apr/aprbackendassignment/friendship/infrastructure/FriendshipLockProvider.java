package com.apr.aprbackendassignment.friendship.infrastructure;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
/** 사용자 쌍의 방향과 무관하게 같은 Redis 분산 락을 제공한다. */
@Component
@RequiredArgsConstructor
public class FriendshipLockProvider {

    private static final String PREFIX_LOCK_KEY = "lock:friendship:";
    private final RedissonClient redissonClient;

    public RLock getLock(Long senderId, Long receiverId) {
        String lockKey = PREFIX_LOCK_KEY +
                Math.min(senderId, receiverId) + ":" +
                Math.max(senderId, receiverId);

        return redissonClient.getLock(lockKey);
    }
}
