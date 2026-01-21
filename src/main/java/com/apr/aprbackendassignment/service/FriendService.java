package com.apr.aprbackendassignment.service;

import com.apr.aprbackendassignment.common.response.PageResponse;
import com.apr.aprbackendassignment.model.dto.FriendshipDto;
import org.springframework.data.domain.Pageable;

public interface FriendService {
    PageResponse<FriendshipDto> getFriendsList(Pageable pageable);
}
