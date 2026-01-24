package com.apr.aprbackendassignment.service;

import com.apr.aprbackendassignment.common.response.PageResponse;
import com.apr.aprbackendassignment.model.constant.WINDOW_SLIDING;
import com.apr.aprbackendassignment.model.dto.UsersDto;
import com.apr.aprbackendassignment.model.dto.request.FriendRequest;
import com.apr.aprbackendassignment.model.dto.response.FriendsRequestsResponse;
import com.apr.aprbackendassignment.model.dto.response.FriendsResponse;
import org.springframework.data.domain.Pageable;

public interface FriendService {
    PageResponse<FriendsResponse> getFriendsList(Pageable pageable);

    PageResponse<FriendsRequestsResponse> getReceiveFriendsList(Pageable pageable, WINDOW_SLIDING windowSliding);

    void requestFriend(Long xUserId, FriendRequest friendRequest);

    void requestAccept(Long xUserId, String requestId);
}
