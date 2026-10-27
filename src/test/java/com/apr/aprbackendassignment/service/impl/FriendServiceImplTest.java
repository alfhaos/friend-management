package com.apr.aprbackendassignment.service.impl;

import com.apr.aprbackendassignment.common.exception.FriendshipError;
import com.apr.aprbackendassignment.common.exception.FriendshipException;
import com.apr.aprbackendassignment.common.request.PageRequestParam;
import com.apr.aprbackendassignment.common.response.CommResponseStatus;
import com.apr.aprbackendassignment.model.constant.FRIENDSHIP_STATUS;
import com.apr.aprbackendassignment.model.constant.WINDOW_SLIDING;
import com.apr.aprbackendassignment.model.dto.response.FriendsRequestsResponse;
import com.apr.aprbackendassignment.model.dto.response.FriendsResponse;
import com.apr.aprbackendassignment.model.entity.Friendship;
import com.apr.aprbackendassignment.model.entity.Users;
import com.apr.aprbackendassignment.service.FriendService;
import com.apr.aprbackendassignment.support.ConcurrentResults;
import com.apr.aprbackendassignment.support.IntegrationTestSupport;
import com.apr.aprbackendassignment.util.FriendRequestFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.apr.aprbackendassignment.model.constant.FRIENDSHIP_STATUS.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FriendServiceImplTest extends IntegrationTestSupport {

    @Autowired
    private FriendService friendService;
    @Autowired
    private FriendRequestFacade facade;

    @Test
    void listsOnlyCurrentUsersAcceptedFriendsInApprovalOrder() {
        Users current = currentListUser();
        Users older = fixture.user("older");
        Users newer = fixture.user("newer");
        Users outsider = fixture.user("outsider");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime firstApproval = LocalDateTime.of(2025, 1, 1, 12, 0);
        LocalDateTime secondApproval = firstApproval.plusHours(1);
        Friendship approvedLater = fixture.relationshipAt(current, newer, ACCEPTED, secondApproval);
        Friendship approvedEarlier = fixture.relationshipAt(older, current, ACCEPTED, firstApproval);
        // Request/audit order is the reverse of approval order; updated_time is not an approval timestamp.
        jdbc.update("UPDATE friendship SET created_time = ?, requested_at = ?, updated_time = ? WHERE id = ?",
                now.minusDays(2), now.minusDays(2), now.minusDays(3), approvedLater.getId());
        jdbc.update("UPDATE friendship SET created_time = ?, requested_at = ?, updated_time = ? WHERE id = ?",
                now.minusDays(1), now.minusDays(1), now, approvedEarlier.getId());
        fixture.relationship(current, outsider, REQUESTED);
        fixture.relationship(older, outsider, ACCEPTED);

        var response = friendService.getFriendsList(new PageRequestParam(0, 1, "approvedAt,desc").toPageable());

        assertThat(response.getTotalCount()).isEqualTo(2);
        assertThat(response.getTotalPages()).isEqualTo(2);
        assertThat(response.getItems()).extracting(FriendsResponse::getUserId).containsExactly(newer.getId());
        assertThat(response.getItems().getFirst().getApprovedAt()).isEqualTo(secondApproval);

        var secondPage = friendService.getFriendsList(new PageRequestParam(1, 1, "approvedAt,desc").toPageable());
        assertThat(secondPage.getItems()).extracting(FriendsResponse::getUserId).containsExactly(older.getId());
        assertThat(secondPage.getItems().getFirst().getApprovedAt()).isEqualTo(firstApproval);
    }

    @Test
    void listsOnlyIncomingPendingRequestsInsideWindow() {
        Users current = currentListUser();
        Users recent = fixture.user("recent");
        Users old = fixture.user("old");
        Users outgoing = fixture.user("outgoing");
        Users accepted = fixture.user("accepted");
        Users rejected = fixture.user("rejected");
        LocalDateTime now = LocalDateTime.now();
        Friendship included = fixture.relationshipAt(recent, current, REQUESTED, now.minusMinutes(5));
        fixture.relationshipAt(old, current, REQUESTED, now.minusDays(3));
        fixture.relationship(current, outgoing, REQUESTED);
        fixture.relationship(accepted, current, ACCEPTED);
        fixture.relationship(rejected, current, REJECTED);

        var response = friendService.getReceiveFriendsList(
                new PageRequestParam(0, 30, "requestedAt,desc").toPageable(), WINDOW_SLIDING.DAY);

        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getItems()).extracting(FriendsRequestsResponse::getRequestId)
                .containsExactly(included.getId().toString());
        assertThat(response.getItems()).extracting(FriendsRequestsResponse::getRequestUserId)
                .containsExactly(recent.getId());
    }

    @Test
    void incomingWindowIncludesStartAndExcludesEnd() {
        Users receiver = fixture.user("receiver");
        LocalDateTime start = LocalDateTime.of(2025, 1, 1, 12, 0);
        LocalDateTime end = start.plusDays(1);
        fixture.relationshipAt(fixture.user("before-start"), receiver, REQUESTED, start.minusSeconds(1));
        Friendship atStart = fixture.relationshipAt(fixture.user("at-start"), receiver, REQUESTED, start);
        Friendship afterStart = fixture.relationshipAt(fixture.user("after-start"), receiver, REQUESTED,
                start.plusSeconds(1));
        Friendship beforeEnd = fixture.relationshipAt(fixture.user("before-end"), receiver, REQUESTED,
                end.minusSeconds(1));
        fixture.relationshipAt(fixture.user("at-end"), receiver, REQUESTED, end);
        fixture.relationshipAt(fixture.user("after-end"), receiver, REQUESTED, end.plusSeconds(1));

        var page = friendships.getFriendsReceiveListInWindow(receiver.getId(), start, end,
                new PageRequestParam(0, 30, "requestedAt,desc").toPageable());

        assertThat(page.getContent()).extracting(Friendship::getId)
                .containsExactlyInAnyOrder(atStart.getId(), afterStart.getId(), beforeEnd.getId());
    }

    @Test
    void listsAllIncomingRequestsWithoutTimeWindowInRequestOrder() {
        Users current = currentListUser();
        Users old = fixture.user("old");
        Users recent = fixture.user("recent");
        LocalDateTime now = LocalDateTime.now();
        Friendship olderRequest = fixture.relationshipAt(old, current, REQUESTED, now.minusDays(120));
        Friendship newerRequest = fixture.relationshipAt(recent, current, REQUESTED, now.minusMinutes(1));

        var response = friendService.getReceiveFriendsList(
                new PageRequestParam(0, 30, "requestedAt,desc").toPageable(), WINDOW_SLIDING.OVER);

        assertThat(response.getTotalCount()).isEqualTo(2);
        assertThat(response.getItems()).extracting(FriendsRequestsResponse::getRequestId)
                .containsExactly(newerRequest.getId().toString(), olderRequest.getId().toString());
    }

    @Test
    void createsPendingRequestAndCommitsBeforeFacadeReturns() {
        Users requester = fixture.user("requester");
        Users receiver = fixture.user("receiver");

        facade.requestFriend(requester.getId(), receiver.getId());

        // A separate repository transaction observes the commit; no test transaction surrounds the Facade.
        Friendship saved = friendships.findByRequesterIdAndReceiverId(requester.getId(), receiver.getId());
        assertThat(saved).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(REQUESTED);
        assertThat(saved.getRequester().getId()).isEqualTo(requester.getId());
        assertThat(saved.getReceiver().getId()).isEqualTo(receiver.getId());
        assertThat(saved.getCreatedTime()).isNotNull();
        assertThat(friendships.count()).isEqualTo(1);
    }

    @Test
    void rejectsSelfRequestWithoutCreatingRelationship() {
        Users requester = fixture.user("requester");
        assertFailure(() -> facade.requestFriend(requester.getId(), requester.getId()),
                FriendshipError.SELF_FRIEND_REQUEST);
        assertThat(friendships.count()).isZero();
    }

    @Test
    void rejectsMissingTargetWithoutCreatingRelationship() {
        Users requester = fixture.user("requester");
        Users removed = fixture.user("removed");
        users.deleteById(removed.getId());
        assertFailure(() -> facade.requestFriend(requester.getId(), removed.getId()),
                FriendshipError.NOT_FOUND_USER);
        assertThat(friendships.count()).isZero();
    }

    @Test
    void rejectsReverseDuplicateWithoutCreatingSecondRelationship() {
        Users requester = fixture.user("requester");
        Users receiver = fixture.user("receiver");
        Friendship original = fixture.relationship(requester, receiver, REQUESTED);
        assertFailure(() -> facade.requestFriend(receiver.getId(), requester.getId()),
                FriendshipError.ALREADY_RREQUESTED_FRIENDSHIP);
        assertThat(friendships.count()).isEqualTo(1);
        assertThat(reload(original).getStatus()).isEqualTo(REQUESTED);
    }

    @Test
    void reusesRejectedRequestOnlyForTheSameDirection() {
        Users requester = fixture.user("requester");
        Users receiver = fixture.user("receiver");
        Users unrelated = fixture.user("unrelated");
        Friendship rejected = fixture.relationship(requester, receiver, REJECTED);
        Friendship untouched = fixture.relationship(requester, unrelated, REJECTED);

        facade.requestFriend(requester.getId(), receiver.getId());

        Friendship reused = friendships.findByRequesterIdAndReceiverId(requester.getId(), receiver.getId());
        assertThat(reused.getId()).isEqualTo(rejected.getId());
        assertThat(reused.getStatus()).isEqualTo(REQUESTED);
        assertThat(reused.getApprovedAt()).isNull();
        assertThat(reused.getRequester().getId()).isEqualTo(requester.getId());
        assertThat(reused.getReceiver().getId()).isEqualTo(receiver.getId());
        assertThat(reload(untouched).getStatus()).isEqualTo(REJECTED);
        assertThat(friendships.count()).isEqualTo(2);
    }

    @Test
    void reusedRejectedRequestAppearsInTodaysIncomingRequests() {
        Users receiver = currentListUser();
        Users requester = fixture.user("requester");
        Users earlierRequester = fixture.user("earlier");
        LocalDateTime previousTime = LocalDateTime.now().minusDays(3);
        Friendship rejected = fixture.relationshipAt(requester, receiver, REJECTED, previousTime);
        Friendship earlierToday = fixture.relationshipAt(earlierRequester, receiver, REQUESTED,
                LocalDateTime.now().toLocalDate().atStartOfDay());

        facade.requestFriend(requester.getId(), receiver.getId());

        Friendship reused = friendships.findByRequesterIdAndReceiverId(requester.getId(), receiver.getId());
        assertThat(reused.getId()).isEqualTo(rejected.getId());
        assertThat(reused.getStatus()).isEqualTo(REQUESTED);
        assertThat(reused.getUpdatedTime()).isAfter(previousTime);
        assertThat(reused.getApprovedAt()).isNull();
        var today = friendService.getReceiveFriendsList(
                new PageRequestParam(0, 30, "requestedAt,desc").toPageable(), WINDOW_SLIDING.DAY);
        assertThat(today.getTotalCount()).isEqualTo(2);
        assertThat(today.getItems()).extracting(FriendsRequestsResponse::getRequestId)
                .containsExactly(rejected.getId().toString(), earlierToday.getId().toString());
        assertThat(today.getItems()).extracting(FriendsRequestsResponse::getRequestUserId)
                .containsExactly(requester.getId(), earlierRequester.getId());
        assertThat(today.getItems().getFirst().getRequestedAt().toLocalDate())
                .isEqualTo(LocalDateTime.now().toLocalDate());
        assertThat(today.getItems().getFirst().getRequestedAt())
                .isAfter(today.getItems().get(1).getRequestedAt());
    }

    @Test
    void createsNewReverseRequestWhilePreservingRejectedHistory() {
        Users first = fixture.user("first");
        Users second = fixture.user("second");
        Friendship rejected = fixture.relationship(first, second, REJECTED);

        facade.requestFriend(second.getId(), first.getId());

        Friendship reverse = friendships.findByRequesterIdAndReceiverId(second.getId(), first.getId());
        assertThat(reverse.getId()).isNotEqualTo(rejected.getId());
        assertThat(reverse.getStatus()).isEqualTo(REQUESTED);
        assertThat(reverse.getRequester().getId()).isEqualTo(second.getId());
        assertThat(reverse.getReceiver().getId()).isEqualTo(first.getId());
        assertThat(reload(rejected).getStatus()).isEqualTo(REJECTED);
        assertThat(friendships.count()).isEqualTo(2);

        assertFailure(() -> facade.requestFriend(second.getId(), first.getId()),
                FriendshipError.ALREADY_RREQUESTED_FRIENDSHIP);
        assertThat(friendships.count()).isEqualTo(2);
    }

    @Test
    void acceptsPendingRequestForBothUsers() {
        Users requester = fixture.user("requester");
        Users receiver = fixture.user("receiver");
        Friendship pending = fixture.relationship(requester, receiver, REQUESTED);
        LocalDateTime requestedAt = reload(pending).getRequestedAt();
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        friendService.requestAccept(receiver.getId(), pending.getId());
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);
        Friendship accepted = reload(pending);
        assertThat(accepted.getStatus()).isEqualTo(ACCEPTED);
        assertThat(accepted.getApprovedAt()).isBetween(before, after);
        assertThat(accepted.getRequestedAt()).isEqualTo(requestedAt);
        assertThat(friendships.countUsersFriends(requester.getId(), ACCEPTED)).isEqualTo(1);
        assertThat(friendships.countUsersFriends(receiver.getId(), ACCEPTED)).isEqualTo(1);
    }

    @Test
    void rejectsPendingRequestWithoutAddingFriends() {
        Users requester = fixture.user("requester");
        Users receiver = fixture.user("receiver");
        Friendship pending = fixture.relationship(requester, receiver, REQUESTED);
        friendService.requestReject(receiver.getId(), pending.getId());
        assertThat(reload(pending).getStatus()).isEqualTo(REJECTED);
        assertThat(reload(pending).getApprovedAt()).isNull();
        assertThat(friendships.countUsersFriends(requester.getId(), ACCEPTED)).isZero();
        assertThat(friendships.countUsersFriends(receiver.getId(), ACCEPTED)).isZero();
    }

    @Test
    void preventsThirdPartyFromAcceptingOrRejecting() {
        Users requester = fixture.user("requester");
        Users receiver = fixture.user("receiver");
        Users outsider = fixture.user("outsider");
        Friendship pending = fixture.relationship(requester, receiver, REQUESTED);
        assertFailure(() -> friendService.requestAccept(outsider.getId(), pending.getId()),
                FriendshipError.ONLY_RECEIVER_CAN_PROCESS);
        assertFailure(() -> friendService.requestReject(outsider.getId(), pending.getId()),
                FriendshipError.ONLY_RECEIVER_CAN_PROCESS);
        assertThat(reload(pending).getStatus()).isEqualTo(REQUESTED);
    }

    @ParameterizedTest
    @EnumSource(value = FRIENDSHIP_STATUS.class, names = {"ACCEPTED", "REJECTED"})
    void preventsProcessingCompletedRequest(FRIENDSHIP_STATUS status) {
        Users requester = fixture.user("requester");
        Users receiver = fixture.user("receiver");
        Friendship completed = fixture.relationship(requester, receiver, status);
        assertFailure(() -> friendService.requestAccept(receiver.getId(), completed.getId()),
                FriendshipError.REQUEST_STATUS_ERROR);
        assertFailure(() -> friendService.requestReject(receiver.getId(), completed.getId()),
                FriendshipError.REQUEST_STATUS_ERROR);
        assertThat(reload(completed).getStatus()).isEqualTo(status);
    }

    @Test
    void concurrentOppositeRequestsCreateExactlyOneRelationship() throws Exception {
        Users first = fixture.user("first");
        Users second = fixture.user("second");
        Users outsider = fixture.user("outsider");
        fixture.relationship(first, outsider, REQUESTED);
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var forward = executor.submit(() -> requestAfterStart(first, second, ready, start));
            var reverse = executor.submit(() -> requestAfterStart(second, first, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).as("workers are ready").isTrue();
            start.countDown();
            assertThat(ConcurrentResults.collect(List.of(forward, reverse), 15))
                    .containsExactlyInAnyOrder(CommResponseStatus.SUCCESS,
                            CommResponseStatus.ALREADY_RREQUESTED_FRIENDSHIP);
            var pair = friendships.findAll().stream()
                    .filter(f -> (f.getRequester().getId().equals(first.getId())
                            && f.getReceiver().getId().equals(second.getId()))
                            || (f.getRequester().getId().equals(second.getId())
                            && f.getReceiver().getId().equals(first.getId())))
                    .toList();
            assertThat(pair).hasSize(1);
            assertThat(pair.getFirst().getStatus()).isEqualTo(REQUESTED);
            assertThat(friendships.count()).isEqualTo(2);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).as("workers terminate before cleanup").isTrue();
        }
    }

    private CommResponseStatus requestAfterStart(Users requester, Users receiver,
                                                CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Start signal timed out");
        }
        try {
            facade.requestFriend(requester.getId(), receiver.getId());
            return CommResponseStatus.SUCCESS;
        } catch (FriendshipException exception) {
            return CommResponseStatus.valueOf(exception.getError().name());
        }
    }

    private Users currentListUser() {
        Users current = fixture.user("current");
        // The current API fixes list queries to ID 1. RESTART IDENTITY makes that explicit and reproducible.
        assertThat(current.getId()).isEqualTo(1L);
        return current;
    }

    private Friendship reload(Friendship relationship) {
        return friendships.findById(relationship.getId()).orElseThrow();
    }

    private void assertFailure(Runnable action, FriendshipError error) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(FriendshipException.class,
                exception -> assertThat(exception.getError()).isEqualTo(error));
    }
}
