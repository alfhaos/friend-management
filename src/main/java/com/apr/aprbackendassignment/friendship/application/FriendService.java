package com.apr.aprbackendassignment.friendship.application;

import com.apr.aprbackendassignment.common.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface FriendService {
    PageResponse<FriendsResponse> getFriendsList(Pageable pageable);

    PageResponse<FriendsRequestsResponse> getReceiveFriendsList(Pageable pageable, RequestWindow requestWindow);

    void requestFriend(Long actorId, Long targetUserId);

    void requestAccept(Long actorId, UUID requestId);
    void requestReject(Long actorId, UUID requestId);
}
