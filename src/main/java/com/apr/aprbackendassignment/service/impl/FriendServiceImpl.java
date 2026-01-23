package com.apr.aprbackendassignment.service.impl;

import com.apr.aprbackendassignment.common.exception.CommException;
import com.apr.aprbackendassignment.common.response.CommResponseStatus;
import com.apr.aprbackendassignment.common.response.PageResponse;
import com.apr.aprbackendassignment.model.constant.FRIENDSHIP_STATUS;
import com.apr.aprbackendassignment.model.constant.WINDOW_SLIDING;
import com.apr.aprbackendassignment.model.dto.request.FriendRequest;
import com.apr.aprbackendassignment.model.dto.response.FriendsRequestsResponse;
import com.apr.aprbackendassignment.model.dto.response.FriendsResponse;
import com.apr.aprbackendassignment.model.entity.Friendship;
import com.apr.aprbackendassignment.model.entity.Users;
import com.apr.aprbackendassignment.repository.FriendshipJPARepository;
import com.apr.aprbackendassignment.repository.UsersJPARepository;
import com.apr.aprbackendassignment.service.FriendService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

    private final FriendshipJPARepository friendshipJPARepository;
    private final UsersJPARepository usersJPARepository;
    // 목록 조회 기능 - 현재 고정된 사용자 ID (예: 1L) 사용
    private static final Long CURRENT_USER_ID = 1L;
    @Transactional(readOnly = true)
    @Override
    public PageResponse<FriendsResponse> getFriendsList(Pageable pageable) {
        Users currentUser = usersJPARepository.findById(CURRENT_USER_ID)
                .orElseThrow(() -> new CommException(CommResponseStatus.NOT_FOUND_USER));

        Page<Friendship> result = friendshipJPARepository.getFriendsList(currentUser.getId(), FRIENDSHIP_STATUS.ACCEPTED, pageable);
        Page<FriendsResponse> dtoPage = FriendsResponse.fromEntityPage(result, currentUser.getId());

        return new PageResponse<>(
                dtoPage.getTotalPages(),
                (int) dtoPage.getTotalElements(),
                dtoPage.getContent()
        );
    }
    @Transactional(readOnly = true)
    @Override
    public PageResponse<FriendsRequestsResponse> getReceiveFriendsList(Pageable pageable, WINDOW_SLIDING windowSliding) {
        Users currentUser = usersJPARepository.findById(CURRENT_USER_ID)
                .orElseThrow(() -> new CommException(CommResponseStatus.NOT_FOUND_USER));

        LocalDateTime time = windowSliding.calculateTimeWindow();
        LocalDateTime start = null;
        LocalDateTime end = null;

        // over일 경우 null로 넘긴다.
        if (time != null) {
            LocalDate date = time.toLocalDate();
            start = date.atStartOfDay();
            end = date.plusDays(1).atStartOfDay();
        }
        Page<Friendship> result = friendshipJPARepository.getFriendsReceiveList(currentUser.getId(), start, end, pageable);
        Page<FriendsRequestsResponse> dtoPage =  FriendsRequestsResponse.fromEntityPage(result);
        return new PageResponse<>(
                dtoPage.getTotalPages(),
                (int) dtoPage.getTotalElements(),
                dtoPage.getContent()
        );
    }

    @Transactional
    @Override
    public void requestFriend(Long xUserId, FriendRequest friendRequest) {
        Users currentUser = usersJPARepository.findById(xUserId)
                .orElseThrow(() -> new CommException(CommResponseStatus.NOT_FOUND_USER));

        Long currentUserId = currentUser.getId();

        // 조회했을떄 존재하지 않는 상대방일 경우 예외 처리
        Users targetUser = usersJPARepository.findById(friendRequest.getTargetUserId())
                .orElseThrow(() -> new CommException(CommResponseStatus.NOT_FOUND_USER));
        Long targetUserId = targetUser.getId();

        // 자기자신에게 친구 요청 보낼 경우 예외 처리
        if(currentUserId.equals(targetUserId)) {
            throw new CommException(CommResponseStatus.SELF_FRIEND_REQUEST);
        }
        // 거절시 재요청 가능 요구사항
        Friendship friendship = friendshipJPARepository.findFriendRequest(currentUser.getId(), targetUser.getId());
        // 만약 거절 이력이 있을경우 해당 데이터 상태를 REQUESTED로 변경
        if(friendship != null) {
            if(friendship.getStatus().equals(FRIENDSHIP_STATUS.REJECTED)) {
                friendship.updateRequestedAt(LocalDateTime.now());
                friendship.updateStatus(FRIENDSHIP_STATUS.REQUESTED);
            } else {
                throw new CommException(CommResponseStatus.ALREADY_REQUESTED_FRIEND);
            }
        } else {
            Friendship friendShip =
                    Friendship.create(
                            currentUser,
                            targetUser,
                            FRIENDSHIP_STATUS.REQUESTED
                    );
            friendshipJPARepository.save(friendShip);
        }
    }
}