package com.apr.aprbackendassignment.friendship.application;

import com.apr.aprbackendassignment.friendship.domain.Friendship;
import com.apr.aprbackendassignment.friendship.infrastructure.FriendshipJPARepository;
import com.apr.aprbackendassignment.user.domain.Users;
import com.apr.aprbackendassignment.user.infrastructure.UsersJPARepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FriendRequestWindowBoundaryTest {

    @Test
    void dayWindowQueriesExactlyOneDayBeforeTheCurrentTime() {
        FriendshipJPARepository friendships = mock(FriendshipJPARepository.class);
        UsersJPARepository users = mock(UsersJPARepository.class);
        Users currentUser = mock(Users.class);
        when(currentUser.getId()).thenReturn(1L);
        when(users.findById(1L)).thenReturn(Optional.of(currentUser));
        PageRequest pageable = PageRequest.of(0, 10);
        ArgumentCaptor<LocalDateTime> start = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> end = ArgumentCaptor.forClass(LocalDateTime.class);
        when(friendships.getFriendsReceiveListInWindow(eq(1L), start.capture(), end.capture(), eq(pageable)))
                .thenReturn(Page.empty(pageable));
        Clock clock = Clock.fixed(Instant.parse("2025-08-22T12:00:00Z"), ZoneOffset.UTC);
        FriendServiceImpl service = new FriendServiceImpl(friendships, users,
                new FriendLimitProperties(), clock);

        service.getReceiveFriendsList(pageable, RequestWindow.DAY);

        assertThat(start.getValue()).isEqualTo(LocalDateTime.of(2025, 8, 21, 12, 0));
        assertThat(end.getValue()).isEqualTo(LocalDateTime.of(2025, 8, 22, 12, 0));
    }

    @Test
    void overWindowQueriesStrictlyBeforeNinetyDaysAgo() {
        FriendshipJPARepository friendships = mock(FriendshipJPARepository.class);
        UsersJPARepository users = mock(UsersJPARepository.class);
        Users currentUser = mock(Users.class);
        when(currentUser.getId()).thenReturn(1L);
        when(users.findById(1L)).thenReturn(Optional.of(currentUser));
        PageRequest pageable = PageRequest.of(0, 10);
        ArgumentCaptor<LocalDateTime> before = ArgumentCaptor.forClass(LocalDateTime.class);
        when(friendships.getFriendsReceiveListBefore(eq(1L), before.capture(), eq(pageable)))
                .thenReturn(Page.empty(pageable));
        Clock clock = Clock.fixed(Instant.parse("2025-08-22T12:00:00Z"), ZoneOffset.UTC);
        FriendServiceImpl service = new FriendServiceImpl(friendships, users,
                new FriendLimitProperties(), clock);

        service.getReceiveFriendsList(pageable, RequestWindow.OVER);

        assertThat(before.getValue()).isEqualTo(LocalDateTime.of(2025, 5, 24, 12, 0));
    }
}
