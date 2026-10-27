package com.apr.aprbackendassignment.service.impl;

import com.apr.aprbackendassignment.common.exception.FriendshipException;
import com.apr.aprbackendassignment.common.response.CommResponseStatus;
import com.apr.aprbackendassignment.model.entity.Friendship;
import com.apr.aprbackendassignment.model.entity.Users;
import com.apr.aprbackendassignment.service.FriendService;
import com.apr.aprbackendassignment.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static com.apr.aprbackendassignment.model.constant.FRIENDSHIP_STATUS.ACCEPTED;
import static com.apr.aprbackendassignment.model.constant.FRIENDSHIP_STATUS.REQUESTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestPropertySource(properties = "limit.max-friend=1")
class FriendshipAtLimitStatusTest extends IntegrationTestSupport {

    @Autowired
    private FriendService friendService;

    @Test
    void acceptingSameRequestAtFriendLimitReportsCompletedStatus() {
        Users requester = fixture.user("requester");
        Users receiver = fixture.user("receiver");
        Friendship pending = fixture.relationship(requester, receiver, REQUESTED);

        friendService.requestAccept(receiver.getId(), pending.getId());

        assertThatThrownBy(() -> friendService.requestAccept(receiver.getId(), pending.getId()))
                .isInstanceOfSatisfying(FriendshipException.class,
                        error -> assertThat(error.getError().name()).isEqualTo(CommResponseStatus.REQUEST_STATUS_ERROR.name()));
        assertThat(friendships.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(ACCEPTED);
        assertThat(friendships.countUsersFriends(requester.getId(), ACCEPTED)).isEqualTo(1);
        assertThat(friendships.countUsersFriends(receiver.getId(), ACCEPTED)).isEqualTo(1);
    }
}
