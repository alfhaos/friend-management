package com.apr.aprbackendassignment.friendship.application;

import com.apr.aprbackendassignment.friendship.domain.FriendshipException;
import com.apr.aprbackendassignment.common.response.CommResponseStatus;
import com.apr.aprbackendassignment.friendship.domain.Friendship;
import com.apr.aprbackendassignment.user.domain.Users;
import com.apr.aprbackendassignment.friendship.application.FriendService;
import com.apr.aprbackendassignment.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.apr.aprbackendassignment.friendship.domain.FRIENDSHIP_STATUS.REQUESTED;
import static org.assertj.core.api.Assertions.assertThat;

class FriendshipAuthorizationLockTest extends IntegrationTestSupport {

    @Autowired
    private FriendService friendService;
    @Autowired
    private TransactionTemplate transactions;

    @Test
    void nonReceiverCannotAcceptWhileParticipantRowIsLocked() throws Exception {
        assertRejectedBeforeParticipantLock(true);
    }

    @Test
    void nonReceiverCannotRejectWhileParticipantRowIsLocked() throws Exception {
        assertRejectedBeforeParticipantLock(false);
    }

    private void assertRejectedBeforeParticipantLock(boolean accept) throws Exception {
        Users requester = fixture.user("requester");
        Users receiver = fixture.user("receiver");
        Users outsider = fixture.user("outsider");
        Friendship pending = fixture.relationship(requester, receiver, REQUESTED);
        var executor = Executors.newFixedThreadPool(2);
        var lockAcquired = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var blocker = executor.submit(() -> transactions.execute(status -> {
                jdbc.queryForObject("SELECT user_id FROM users WHERE user_id = ? FOR UPDATE",
                        Long.class, requester.getId());
                lockAcquired.countDown();
                await(release);
                return null;
            }));
            assertThat(lockAcquired.await(10, TimeUnit.SECONDS)).as("participant row locked").isTrue();

            var denied = executor.submit(() -> {
                try {
                    if (accept) {
                        friendService.requestAccept(outsider.getId(), pending.getId());
                    } else {
                        friendService.requestReject(outsider.getId(), pending.getId());
                    }
                    return CommResponseStatus.SUCCESS;
                } catch (FriendshipException error) {
                    return CommResponseStatus.valueOf(error.getError().name());
                }
            });
            assertThat(denied.get(2, TimeUnit.SECONDS))
                    .as("unauthorized request rejected without waiting for participant lock")
                    .isEqualTo(CommResponseStatus.ONLY_RECEIVER_CAN_PROCESS);
            release.countDown();
            blocker.get(10, TimeUnit.SECONDS);
            assertThat(friendships.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(REQUESTED);
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).as("workers terminated").isTrue();
        }
    }

    private void await(CountDownLatch release) {
        try {
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Database lock release timed out");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Database lock wait interrupted", error);
        }
    }
}
