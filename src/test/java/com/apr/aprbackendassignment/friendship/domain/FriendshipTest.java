package com.apr.aprbackendassignment.friendship.domain;

import com.apr.aprbackendassignment.user.domain.Users;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static com.apr.aprbackendassignment.friendship.domain.FRIENDSHIP_STATUS.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FriendshipTest {

    private static final LocalDateTime REQUEST_TIME = LocalDateTime.of(2025, 1, 1, 12, 0);
    private static final LocalDateTime LATER = REQUEST_TIME.plusHours(1);
    private final Users requester = user(101L);
    private final Users receiver = user(202L);

    @Test
    void requestAndAcceptRecordDistinctBusinessTimes() {
        Friendship friendship = Friendship.request(requester, receiver, REQUEST_TIME);

        assertThat(friendship.getStatus()).isEqualTo(REQUESTED);
        assertThat(friendship.getRequestedAt()).isEqualTo(REQUEST_TIME);
        assertThat(friendship.getApprovedAt()).isNull();
        friendship.accept(receiver.getId(), LATER);
        assertThat(friendship.getStatus()).isEqualTo(ACCEPTED);
        assertThat(friendship.getRequestedAt()).isEqualTo(REQUEST_TIME);
        assertThat(friendship.getApprovedAt()).isEqualTo(LATER);
    }

    @Test
    void rejectionAndSameDirectionReapplicationKeepIdentityAndRefreshRequestTime() {
        Friendship friendship = Friendship.request(requester, receiver, REQUEST_TIME);
        var id = friendship.getId();
        friendship.reject(receiver.getId());
        assertThat(friendship.getStatus()).isEqualTo(REJECTED);
        assertThat(friendship.getApprovedAt()).isNull();

        friendship.requestAgain(requester.getId(), LATER);

        assertThat(friendship.getId()).isEqualTo(id);
        assertThat(friendship.getRequester()).isSameAs(requester);
        assertThat(friendship.getReceiver()).isSameAs(receiver);
        assertThat(friendship.getStatus()).isEqualTo(REQUESTED);
        assertThat(friendship.getRequestedAt()).isEqualTo(LATER);
        assertThat(friendship.getApprovedAt()).isNull();
    }

    @Test
    void requestToSelfIsRejected() {
        assertThatThrownBy(() -> Friendship.request(requester, requester, REQUEST_TIME))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unauthorizedActionsLeavePendingRelationshipUnchanged() {
        Friendship friendship = Friendship.request(requester, receiver, REQUEST_TIME);
        assertThatThrownBy(() -> friendship.accept(requester.getId(), LATER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> friendship.reject(requester.getId()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(friendship.getStatus()).isEqualTo(REQUESTED);
        assertThat(friendship.getRequestedAt()).isEqualTo(REQUEST_TIME);
        assertThat(friendship.getApprovedAt()).isNull();
    }

    @Test
    void completedRelationshipCannotBeProcessedAgainOrLoseApprovalTime() {
        Friendship friendship = Friendship.request(requester, receiver, REQUEST_TIME);
        friendship.accept(receiver.getId(), LATER);
        assertThatThrownBy(() -> friendship.accept(receiver.getId(), LATER.plusHours(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> friendship.reject(receiver.getId()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> friendship.requestAgain(requester.getId(), LATER.plusHours(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(friendship.getStatus()).isEqualTo(ACCEPTED);
        assertThat(friendship.getRequestedAt()).isEqualTo(REQUEST_TIME);
        assertThat(friendship.getApprovedAt()).isEqualTo(LATER);
    }

    @Test
    void onlyOriginalRequesterCanReapplyRejectedRelationship() {
        Friendship friendship = Friendship.request(requester, receiver, REQUEST_TIME);
        friendship.reject(receiver.getId());
        assertThatThrownBy(() -> friendship.requestAgain(receiver.getId(), LATER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(friendship.getStatus()).isEqualTo(REJECTED);
        assertThat(friendship.getRequestedAt()).isEqualTo(REQUEST_TIME);
        assertThat(friendship.getApprovedAt()).isNull();
    }

    @Test
    void missingTransitionTimeDoesNotPartiallyChangeRelationship() {
        Friendship pending = Friendship.request(requester, receiver, REQUEST_TIME);
        assertThatThrownBy(() -> pending.accept(receiver.getId(), null))
                .isInstanceOf(NullPointerException.class);
        assertThat(pending.getStatus()).isEqualTo(REQUESTED);
        assertThat(pending.getRequestedAt()).isEqualTo(REQUEST_TIME);
        assertThat(pending.getApprovedAt()).isNull();

        pending.reject(receiver.getId());
        assertThatThrownBy(() -> pending.requestAgain(requester.getId(), null))
                .isInstanceOf(NullPointerException.class);
        assertThat(pending.getStatus()).isEqualTo(REJECTED);
        assertThat(pending.getRequestedAt()).isEqualTo(REQUEST_TIME);
        assertThat(pending.getApprovedAt()).isNull();
    }

    private static Users user(long id) {
        Users user = Users.create("user-" + id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
