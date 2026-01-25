package com.apr.aprbackendassignment.util;

import com.apr.aprbackendassignment.common.exception.CommException;
import com.apr.aprbackendassignment.common.response.CommResponseStatus;
import com.apr.aprbackendassignment.model.dto.request.FriendRequest;
import com.apr.aprbackendassignment.service.FriendService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class FriendRequestFacade {

    private final RedisLockUtil redisLockUtil;
    private final FriendService friendService;

    public void requestFriend(Long currentUserId, FriendRequest friendRequest) {
        // ID를 정렬하여 항상 동일한 키를 생성하는지 확인 (redisLockUtil 내부 로직)
        RLock lock = redisLockUtil.getLock(currentUserId, friendRequest.getTargetUserId());

        try {
            boolean isLocked = lock.tryLock(5, 10, TimeUnit.SECONDS);
            if (!isLocked) {
                throw new CommException(CommResponseStatus.CONCURRENT_FRIEND_REQUEST);
            }

            // 핵심: 락이 걸린 상태에서 트랜잭션 메서드를 호출
            // 이 메서드가 리턴될 때 커밋이 완료됩니다.
            friendService.requestFriend(currentUserId, friendRequest);

        } catch (InterruptedException e) {
            // 인터럽트 발생 시 현재 스레드의 인터럽트 상태를 복원
            Thread.currentThread().interrupt();
            throw new CommException(CommResponseStatus.LOCK_INTERRUPTED);
        } finally {
            // 커밋이 완전히 끝난 후 락을 해제합니다.
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}