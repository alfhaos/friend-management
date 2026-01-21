package com.apr.aprbackendassignment.service;

import com.apr.aprbackendassignment.common.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface FriendService {
    PageResponse<Object> getFriendsList(Pageable pageable);
}
