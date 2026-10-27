package com.apr.aprbackendassignment.service;

import com.apr.aprbackendassignment.common.response.PageResponse;
import com.apr.aprbackendassignment.model.constant.WINDOW_SLIDING;
import com.apr.aprbackendassignment.model.dto.response.FriendsRequestsResponse;
import com.apr.aprbackendassignment.model.dto.response.FriendsResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface FriendService {
    PageResponse<FriendsResponse> getFriendsList(Pageable pageable);

    PageResponse<FriendsRequestsResponse> getReceiveFriendsList(Pageable pageable, WINDOW_SLIDING windowSliding);

    void requestFriend(Long actorId, Long targetUserId);

    void requestAccept(Long actorId, UUID requestId);
    void requestReject(Long actorId, UUID requestId);
}
