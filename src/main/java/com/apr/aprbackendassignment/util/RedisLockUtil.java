package com.apr.aprbackendassignment.util;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
/**
 * =====================================================
 * Class Name   : RedisLockUtil
 * Description  :
 *  - 애플리케이션의 제한 관련 설정을 외부 구성 파일에서 로드하는 클래스

 * 주요 기능
 *  - getLock 메서드: 두 사용자 ID에 대한 분산 락을 생성
 * =====================================================
 */
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
