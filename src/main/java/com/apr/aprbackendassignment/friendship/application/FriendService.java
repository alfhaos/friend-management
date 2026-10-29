package com.apr.aprbackendassignment.friendship.application;

import com.apr.aprbackendassignment.common.response.PageResponse;
import com.apr.aprbackendassignment.common.response.WindowResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface FriendService {
    PageResponse<FriendsResponse> getFriendsList(Pageable pageable);

    WindowResponse<FriendsRequestsResponse> getReceiveFriendsList(
            Pageable pageable, RequestWindow requestWindow);

    void requestFriend(Long actorId, Long targetUserId);

    void requestAccept(Long actorId, UUID requestId);
    void requestReject(Long actorId, UUID requestId);
}
