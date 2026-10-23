package com.apr.aprbackendassignment.util;

import com.apr.aprbackendassignment.common.exception.CommException;
import com.apr.aprbackendassignment.common.response.CommResponseStatus;
import com.apr.aprbackendassignment.model.dto.request.FriendRequest;
import com.apr.aprbackendassignment.service.FriendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.client.RedisException;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.util.concurrent.TimeUnit;
/**
 * =====================================================
 * Class Name   : FriendRequestFacade
 * Description  :
 *  - 친구 요청 처리를 위한 퍼사드 클래스
 *  - Redis 기반의 분산 락을 활용하여 동시성 문제 해결

 * 주요 기능
 *  - requestFriend 메서드: 친구 요청 처리 시 락을 획득하고 해제하는 로직 포함
 * =====================================================
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FriendRequestFacade {

    private final RedisLockUtil redisLockUtil;
    private final FriendService friendService;

    public void requestFriend(Long currentUserId, FriendRequest friendRequest) {
        RLock lock = null;
        boolean acquired = false;

        try {
            try {
                lock = redisLockUtil.getLock(currentUserId, friendRequest.getTargetUserId());
                acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);
            } catch (RedisException | RedisConnectionFailureException e) {
                log.warn("Redis lock unavailable; database constraints will protect friend request", e);
                friendService.requestFriend(currentUserId, friendRequest);
                return;
            }

            if (!acquired) {
                throw new CommException(CommResponseStatus.CONCURRENT_FRIEND_REQUEST);
            }
            friendService.requestFriend(currentUserId, friendRequest);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CommException(CommResponseStatus.LOCK_INTERRUPTED);
        } finally {
            if (acquired) {
                try {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                } catch (RuntimeException e) {
                    // The database transaction has already committed or rolled back.
                    log.warn("Redis lock release failed after friend request processing", e);
                }
            }
        }
    }
}
