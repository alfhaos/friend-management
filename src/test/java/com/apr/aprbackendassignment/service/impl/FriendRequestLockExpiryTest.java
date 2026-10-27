package com.apr.aprbackendassignment.service.impl;

import com.apr.aprbackendassignment.common.exception.FriendshipException;
import com.apr.aprbackendassignment.model.entity.Users;
import com.apr.aprbackendassignment.support.ConcurrentResults;
import com.apr.aprbackendassignment.support.IntegrationTestSupport;
import com.apr.aprbackendassignment.util.FriendRequestFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static com.apr.aprbackendassignment.model.constant.FRIENDSHIP_STATUS.REQUESTED;
import static org.assertj.core.api.Assertions.assertThat;

class FriendRequestLockExpiryTest extends IntegrationTestSupport {

    @Autowired
    private FriendRequestFacade facade;
    @Autowired
    private TransactionTemplate transactions;

    @Test
    void databaseKeepsOneActiveRequestWhenRedisLeaseExpiresBeforeCommit() throws Exception {
        Users first = fixture.user("first");
        Users second = fixture.user("second");
        String key = "lock:friendship:" + first.getId() + ":" + second.getId();
        var executor = Executors.newFixedThreadPool(3);
        var dbLockAcquired = new CountDownLatch(1);
        var releaseDbLock = new CountDownLatch(1);
        try {
            var blocker = executor.submit(() -> transactions.execute(status -> {
                jdbc.queryForObject("SELECT user_id FROM users WHERE user_id = ? FOR UPDATE",
                        Long.class, first.getId());
                dbLockAcquired.countDown();
                await(releaseDbLock);
                return "BLOCKED";
            }));
            assertThat(dbLockAcquired.await(10, TimeUnit.SECONDS)).as("database row locked").isTrue();

            var forward = executor.submit(() -> request(first, second));
            waitUntil(() -> redisson.getLock(key).isLocked());
            assertThat(redisson.getBucket(key).expire(100, TimeUnit.MILLISECONDS)).isTrue();
            waitUntil(() -> !redisson.getBucket(key).isExists());

            var reverse = executor.submit(() -> request(second, first));
            waitUntil(() -> redisson.getLock(key).isLocked());
            releaseDbLock.countDown();

            List<String> outcomes = ConcurrentResults.collect(List.of(blocker, forward, reverse), 15);
            assertThat(outcomes.get(0)).isEqualTo("BLOCKED");
            List<String> results = outcomes.subList(1, outcomes.size());
            assertThat(results).containsExactlyInAnyOrder("SUCCESS", "ALREADY_RREQUESTED_FRIENDSHIP");
            assertThat(friendships.findAll()).hasSize(1);
            assertThat(friendships.findAll().getFirst().getStatus()).isEqualTo(REQUESTED);
        } finally {
            releaseDbLock.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).as("workers terminated").isTrue();
        }
    }

    private String request(Users requester, Users receiver) {
        try {
            facade.requestFriend(requester.getId(), receiver.getId());
            return "SUCCESS";
        } catch (FriendshipException exception) {
            return exception.getError().name();
        }
    }

    private void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Timed out waiting for lock state");
            }
            Thread.sleep(20);
        }
    }

    private void await(CountDownLatch release) {
        try {
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Database lock release timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Database lock wait interrupted", exception);
        }
    }
}
