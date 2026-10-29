package com.apr.aprbackendassignment.friendship.application;

import com.apr.aprbackendassignment.friendship.domain.FriendshipException;
import com.apr.aprbackendassignment.user.domain.Users;
import com.apr.aprbackendassignment.support.ConcurrentResults;
import com.apr.aprbackendassignment.support.IntegrationTestSupport;
import com.apr.aprbackendassignment.friendship.application.FriendRequestFacade;
import com.apr.aprbackendassignment.friendship.infrastructure.FriendshipLockProvider;
import org.junit.jupiter.api.Test;
import org.redisson.client.RedisException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.apr.aprbackendassignment.friendship.domain.FRIENDSHIP_STATUS.REQUESTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

class FriendRequestRedisFallbackTest extends IntegrationTestSupport {

    @Autowired
    private FriendRequestFacade facade;

    @MockitoBean
    private FriendshipLockProvider redisLockUtil;

    @Test
    void redisFailureStillLeavesExactlyOneActiveRequestForOppositeConcurrentCalls() throws Exception {
        Users first = fixture.user("first");
        Users second = fixture.user("second");
        when(redisLockUtil.getLock(anyLong(), anyLong()))
                .thenThrow(new RedisException("simulated Redis connection failure"));
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var forward = executor.submit(() -> requestAfterStart(first, second, ready, start));
            var reverse = executor.submit(() -> requestAfterStart(second, first, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).as("both workers ready").isTrue();
            start.countDown();
            List<String> results = ConcurrentResults.collect(List.of(forward, reverse), 15);
            assertThat(results).containsExactlyInAnyOrder("SUCCESS", "ALREADY_REQUESTED_FRIENDSHIP");
            assertThat(friendships.findAll()).hasSize(1);
            assertThat(friendships.findAll().getFirst().getStatus()).isEqualTo(REQUESTED);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).as("workers terminated").isTrue();
        }
    }

    private String requestAfterStart(Users requester, Users receiver,
                                     CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Start signal timed out");
        }
        try {
            facade.requestFriend(requester.getId(), receiver.getId());
            return "SUCCESS";
        } catch (FriendshipException exception) {
            return exception.getError().name();
        }
    }
}
