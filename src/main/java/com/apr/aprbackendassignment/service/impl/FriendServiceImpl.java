package com.apr.aprbackendassignment.service.impl;

import com.apr.aprbackendassignment.common.exception.CommException;
import com.apr.aprbackendassignment.common.response.CommResponseStatus;
import com.apr.aprbackendassignment.common.response.PageResponse;
import com.apr.aprbackendassignment.model.constant.FRIENDSHIP_STATUS;
import com.apr.aprbackendassignment.model.constant.WINDOW_SLIDING;
import com.apr.aprbackendassignment.model.dto.UsersDto;
import com.apr.aprbackendassignment.model.dto.request.FriendRequest;
import com.apr.aprbackendassignment.model.dto.response.FriendsRequestsResponse;
import com.apr.aprbackendassignment.model.dto.response.FriendsResponse;
import com.apr.aprbackendassignment.model.entity.Friendship;
import com.apr.aprbackendassignment.model.entity.Users;
import com.apr.aprbackendassignment.repository.FriendRepository;
import com.apr.aprbackendassignment.service.FriendService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * =====================================================
 * Class Name   : FriendServiceImpl
 * Description  :
 *  - 친구 관계 관리를 위한 서비스 구현 클래스
 *
 * 주요 기능
 *  - 친구 관계 비즈니스 로직 구현
 *  - 현재 예시에서는 고정된 사용자 ID를 사용하여 친구 목록을 조회
 * =====================================================
 */
@Service
@RequiredArgsConstructor
public class FriendServiceImpl implements FriendService {
    private static final Long CURRENT_USER_ID = 1L;

    private final FriendRepository friendRepository;
    @Transactional(readOnly = true)
    @Override
    public PageResponse<FriendsResponse> getFriendsList(Pageable pageable) {
        UsersDto currentUser = friendRepository.findUserById(CURRENT_USER_ID);
        Page<FriendsResponse> dtoPage = friendRepository.getFriendsList(currentUser.getId(), pageable);

        return new PageResponse<>(
                dtoPage.getTotalPages(),
                (int) dtoPage.getTotalElements(),
                dtoPage.getContent()
        );
    }
    @Transactional(readOnly = true)
    @Override
    public PageResponse<FriendsRequestsResponse> getReceiveFriendsList(Pageable pageable, WINDOW_SLIDING windowSliding) {
        UsersDto currentUser = friendRepository.findUserById(CURRENT_USER_ID);

        Page<FriendsRequestsResponse> dtoPage = friendRepository.getFriendsReceiveList(currentUser.getId(), pageable, windowSliding);
        return new PageResponse<>(
                dtoPage.getTotalPages(),
                (int) dtoPage.getTotalElements(),
                dtoPage.getContent()
        );
    }

    @Transactional
    @Override
    public void requestFriend(Long xUserId, FriendRequest friendRequest) {
        Users currentUser = friendRepository.findUserByIdOrThrow(xUserId);
        Long currentUserId = currentUser.getId();

        // 조회했을떄 존재하지 않는 상대방일 경우 예외 처리
        Users targetUser = friendRepository.findUserByIdOrThrow(friendRequest.getTargetUserId());
        Long targetUserId = targetUser.getId();

        // 자기자신에게 친구 요청 보낼 경우 예외 처리
        if(currentUserId.equals(targetUserId)) {
            throw new CommException(CommResponseStatus.SELF_FRIEND_REQUEST);
        }

        friendRepository.requestFriend(currentUser, targetUser);
    }
}