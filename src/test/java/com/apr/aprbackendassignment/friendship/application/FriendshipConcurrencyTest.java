package com.apr.aprbackendassignment.friendship.application;

import com.apr.aprbackendassignment.friendship.domain.FriendshipException;
import com.apr.aprbackendassignment.common.response.CommResponseStatus;
import com.apr.aprbackendassignment.friendship.domain.Friendship;
import com.apr.aprbackendassignment.user.domain.Users;
import com.apr.aprbackendassignment.friendship.application.FriendService;
import com.apr.aprbackendassignment.support.ConcurrentResults;
import com.apr.aprbackendassignment.support.IntegrationTestSupport;
import com.apr.aprbackendassignment.friendship.application.FriendRequestFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.apr.aprbackendassignment.friendship.domain.FRIENDSHIP_STATUS.ACCEPTED;
import static com.apr.aprbackendassignment.friendship.domain.FRIENDSHIP_STATUS.REJECTED;
import static com.apr.aprbackendassignment.friendship.domain.FRIENDSHIP_STATUS.REQUESTED;
import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "limit.max-friend=2")
class FriendshipConcurrencyTest extends IntegrationTestSupport {

    @Autowired
    private FriendService friendService;
    @Autowired
    private FriendRequestFacade facade;

    @Test
    void concurrentSameDirectionRequestsCreateOneRelationship() throws Exception {
        Users requester = fixture.user("requester");
        Users receiver = fixture.user("receiver");
        Callable<CommResponseStatus> request = () -> process(
                () -> facade.requestFriend(requester.getId(), receiver.getId()));

        assertThat(race(request, request)).containsExactlyInAnyOrder(
                CommResponseStatus.SUCCESS, CommResponseStatus.ALREADY_REQUESTED_FRIENDSHIP);
        assertThat(friendships.count()).isEqualTo(1);
        Friendship stored = friendships.findByRequesterIdAndReceiverId(requester.getId(), receiver.getId());
        assertThat(stored.getStatus()).isEqualTo(REQUESTED);
    }

    @Test
    void concurrentRejectAndSameDirectionRequestPreserveOriginalRequestId() throws Exception {
        Users requester = fixture.user("requester");
        Users receiver = fixture.user("receiver");
        Friendship original = fixture.relationship(requester, receiver, REQUESTED);

        List<CommResponseStatus> outcomes = race(
                () -> process(() -> friendService.requestReject(receiver.getId(), original.getId())),
                () -> process(() -> facade.requestFriend(requester.getId(), receiver.getId())));

        assertThat(outcomes.get(0)).isEqualTo(CommResponseStatus.SUCCESS);
        assertThat(outcomes.get(1)).isIn(
                CommResponseStatus.SUCCESS, CommResponseStatus.ALREADY_REQUESTED_FRIENDSHIP);
        Friendship stored = friendships.findById(original.getId()).orElseThrow();
        assertThat(friendships.count()).isEqualTo(1);
        assertThat(stored.getId()).isEqualTo(original.getId());
        assertThat(stored.getStatus()).isEqualTo(outcomes.get(1) == CommResponseStatus.SUCCESS
                ? REQUESTED : REJECTED);
    }

    @Test
    void concurrentAcceptAndRejectCommitExactlyOneTransition() throws Exception {
        Users requester = fixture.user("requester");
        Users receiver = fixture.user("receiver");
        Friendship pending = fixture.relationship(requester, receiver, REQUESTED);

        List<CommResponseStatus> outcomes = race(
                () -> process(() -> friendService.requestAccept(receiver.getId(), pending.getId())),
                () -> process(() -> friendService.requestReject(receiver.getId(), pending.getId())));

        assertThat(outcomes).containsExactlyInAnyOrder(
                CommResponseStatus.SUCCESS, CommResponseStatus.REQUEST_STATUS_ERROR);
        Friendship stored = friendships.findById(pending.getId()).orElseThrow();
        assertThat(stored.getStatus()).isIn(ACCEPTED, REJECTED);
        int acceptedCount = stored.getStatus() == ACCEPTED ? 1 : 0;
        assertThat(friendships.countUsersFriends(requester.getId(), ACCEPTED)).isEqualTo(acceptedCount);
        assertThat(friendships.countUsersFriends(receiver.getId(), ACCEPTED)).isEqualTo(acceptedCount);
        assertThat(friendships.count()).isEqualTo(1);
    }

    @Test
    void concurrentAcceptsOfOneRequestCommitOnlyOnce() throws Exception {
        Users requester = fixture.user("requester");
        Users receiver = fixture.user("receiver");
        Friendship pending = fixture.relationship(requester, receiver, REQUESTED);
        Callable<CommResponseStatus> accept = () -> process(
                () -> friendService.requestAccept(receiver.getId(), pending.getId()));

        assertThat(race(accept, accept)).containsExactlyInAnyOrder(
                CommResponseStatus.SUCCESS, CommResponseStatus.REQUEST_STATUS_ERROR);
        assertThat(friendships.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(ACCEPTED);
        assertThat(friendships.countUsersFriends(requester.getId(), ACCEPTED)).isEqualTo(1);
        assertThat(friendships.countUsersFriends(receiver.getId(), ACCEPTED)).isEqualTo(1);
    }

    @Test
    void concurrentAcceptsCannotExceedSharedReceiversFriendLimit() throws Exception {
        Users shared = fixture.user("shared-receiver");
        Users existing = fixture.user("existing");
        Users first = fixture.user("first");
        Users second = fixture.user("second");
        fixture.relationship(existing, shared, ACCEPTED);
        Friendship firstPending = fixture.relationship(first, shared, REQUESTED);
        Friendship secondPending = fixture.relationship(second, shared, REQUESTED);

        List<CommResponseStatus> outcomes = race(
                () -> process(() -> friendService.requestAccept(shared.getId(), firstPending.getId())),
                () -> process(() -> friendService.requestAccept(shared.getId(), secondPending.getId())));

        assertThat(outcomes).contains(CommResponseStatus.SUCCESS,
                CommResponseStatus.FRIEND_LIMIT_EXCEEDED_FOR_ACCEPTOR);
        assertThat(friendships.countUsersFriends(shared.getId(), ACCEPTED)).isEqualTo(2);
        assertThat(friendships.countUsersFriends(first.getId(), ACCEPTED)).isLessThanOrEqualTo(2);
        assertThat(friendships.countUsersFriends(second.getId(), ACCEPTED)).isLessThanOrEqualTo(2);
        assertThat(List.of(friendships.findById(firstPending.getId()).orElseThrow().getStatus(),
                friendships.findById(secondPending.getId()).orElseThrow().getStatus()))
                .containsExactlyInAnyOrder(ACCEPTED, REQUESTED);
    }

    @Test
    void concurrentAcceptsCannotExceedSharedRequestersFriendLimit() throws Exception {
        Users shared = fixture.user("shared-requester");
        Users existing = fixture.user("existing");
        Users first = fixture.user("first");
        Users second = fixture.user("second");
        fixture.relationship(shared, existing, ACCEPTED);
        Friendship firstPending = fixture.relationship(shared, first, REQUESTED);
        Friendship secondPending = fixture.relationship(shared, second, REQUESTED);

        List<CommResponseStatus> outcomes = race(
                () -> process(() -> friendService.requestAccept(first.getId(), firstPending.getId())),
                () -> process(() -> friendService.requestAccept(second.getId(), secondPending.getId())));

        assertThat(outcomes).contains(CommResponseStatus.SUCCESS,
                CommResponseStatus.FRIEND_LIMIT_EXCEEDED_REQUESTER);
        assertThat(friendships.countUsersFriends(shared.getId(), ACCEPTED)).isEqualTo(2);
        assertThat(friendships.countUsersFriends(first.getId(), ACCEPTED)).isLessThanOrEqualTo(2);
        assertThat(friendships.countUsersFriends(second.getId(), ACCEPTED)).isLessThanOrEqualTo(2);
        assertThat(List.of(friendships.findById(firstPending.getId()).orElseThrow().getStatus(),
                friendships.findById(secondPending.getId()).orElseThrow().getStatus()))
                .containsExactlyInAnyOrder(ACCEPTED, REQUESTED);
    }

    private CommResponseStatus process(Runnable action) {
        try {
            action.run();
            return CommResponseStatus.SUCCESS;
        } catch (FriendshipException exception) {
            return CommResponseStatus.valueOf(exception.getError().name());
        }
    }

    private List<CommResponseStatus> race(Callable<CommResponseStatus> first,
                                          Callable<CommResponseStatus> second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var firstResult = executor.submit(() -> afterStart(first, ready, start));
            var secondResult = executor.submit(() -> afterStart(second, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).as("both workers ready").isTrue();
            start.countDown();
            return ConcurrentResults.collect(List.of(firstResult, secondResult), 15);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).as("workers terminated").isTrue();
        }
    }

    private CommResponseStatus afterStart(Callable<CommResponseStatus> action,
                                           CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Start signal timed out");
        }
        return action.call();
    }
}
