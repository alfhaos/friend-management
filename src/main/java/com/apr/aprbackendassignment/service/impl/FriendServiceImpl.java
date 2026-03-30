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
import com.apr.aprbackendassignment.util.LimitProperties;
import com.apr.aprbackendassignment.util.RedisLockUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

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
    private final LimitProperties limitProperties;
    private final RedisLockUtil redisLockUtil;
    // 목록 조회 기능 - 현재 고정된 사용자 ID (예: 1L) 사용
    private static final Long CURRENT_USER_ID = 1L;
    @Transactional(readOnly = true)
    @Override
    public PageResponse<FriendsResponse> getFriendsList(Pageable pageable) {
        Users currentUser = getUserOrThrow(CURRENT_USER_ID);

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
        Users currentUser = getUserOrThrow(CURRENT_USER_ID);

        LocalDateTime time = windowSliding.calculateTimeWindow();
        LocalDateTime start = time != null ? time.toLocalDate().atStartOfDay() : null;
        LocalDateTime end = time != null ? LocalDateTime.now() : null;

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
        // 자기자신에게 친구 요청 보낼 경우 예외 처리
        if(xUserId.equals(friendRequest.getTargetUserId())) {
            throw new CommException(CommResponseStatus.SELF_FRIEND_REQUEST);
        }

        Users currentUser = getUserOrThrow(xUserId);

        Users targetUser = getUserOrThrow(friendRequest.getTargetUserId());

        // 이미 받은 요청이 있는지 확인
        Friendship existingRequest = friendshipJPARepository.checkExistingRequest(currentUser.getId(),friendRequest.getTargetUserId());
        if (existingRequest != null) {
            throw new CommException(CommResponseStatus.ALREADY_RREQUESTED_FRIENDSHIP);
        }
        // 자기자신에게 친구 요청 보낼 경우 예외 처리
        if(currentUserId.equals(targetUserId)) {
            throw new CommException(CommResponseStatus.SELF_FRIEND_REQUEST);
        }
        // 거절시 재요청 가능 요구사항
        Friendship friendship = friendshipJPARepository.findByRequesterIdAndReceiverId(currentUser.getId(), targetUser.getId());

        if(existingRequest == null) {
            Friendship friendShip =
                    Friendship.create(
                            currentUser,
                            targetUser,
                            FRIENDSHIP_STATUS.REQUESTED
                    );
            friendshipJPARepository.save(friendShip);
            return;
        }

        if (existingRequest.getStatus().equals(FRIENDSHIP_STATUS.REQUESTED)) {
            throw new CommException(CommResponseStatus.ALREADY_RREQUESTED_FRIENDSHIP);
        }

        if(existingRequest.getStatus().equals(FRIENDSHIP_STATUS.ACCEPTED)) {
            throw new CommException(CommResponseStatus.ALREADY_REQUESTED_FRIEND);
        }

        // 거절시 재요청 가능 요구사항
        // 만약 거절 이력이 있을경우 해당 데이터 상태를 REQUESTED로 변경
        if(existingRequest.getStatus().equals(FRIENDSHIP_STATUS.REJECTED)) {
                existingRequest.updateRequester(currentUser);   // B→A 재요청이면 requester를 B로 변경
                existingRequest.updateReceiver(targetUser);     // receiver를 A로 변경
                existingRequest.updateUpdateAt(LocalDateTime.now());
                existingRequest.updateStatus(FRIENDSHIP_STATUS.REQUESTED);
        }

    }

    @Transactional
    @Override
    public void requestAccept(Long xUserId, String requestId) {
        Users currentUser = getUserOrThrow(xUserId);

        Friendship friendship = friendshipJPARepository.findById(UUID.fromString(requestId))
                .orElseThrow(() -> new CommException(CommResponseStatus.NOT_FOUND_FRIENDSHIP));

        // 수신자가 아닌 경우 예외 처리
        if(!friendship.getReceiver().getId().equals(currentUser.getId())) {
            throw new CommException(CommResponseStatus.ONLY_RECEIVER_CAN_PROCESS);
        }

        Users targetUser = getUserOrThrow(friendship.getRequester().getId());
        Long targetUserId = targetUser.getId();

        int currentUserFriendCnt = friendshipJPARepository.countUsersFriends(currentUser.getId(), FRIENDSHIP_STATUS.ACCEPTED);

        // 현재 로그인 유저의 친구 수 체크
        if(currentUserFriendCnt >= limitProperties.getMaxFriend()) {
            throw new CommException(CommResponseStatus.FRIEND_LIMIT_EXCEEDED_FOR_ACCEPTOR);
        }

        // 상대방 친구 수 제한 체크
        int targetUserFriendCnt = friendshipJPARepository.countUsersFriends(targetUserId, FRIENDSHIP_STATUS.ACCEPTED);
        if(targetUserFriendCnt >= limitProperties.getMaxFriend()) {
            throw new CommException(CommResponseStatus.FRIEND_LIMIT_EXCEEDED_REQUESTER);
        }
        // 요청 상태가 REQUEST가 아닐 경우 예외 처리
        if(!friendship.getStatus().equals(FRIENDSHIP_STATUS.REQUESTED)) {
            throw new CommException(CommResponseStatus.REQUEST_STATUS_ERROR);
        }

        friendship.updateStatus(FRIENDSHIP_STATUS.ACCEPTED);
    }

    @Transactional
    @Override
    public void requestReject(Long xUserId, String requestId) {
        Users currentUser = getUserOrThrow(xUserId);
        Friendship friendship = friendshipJPARepository.findById(UUID.fromString(requestId))
                .orElseThrow(() -> new CommException(CommResponseStatus.NOT_FOUND_FRIENDSHIP));

        // 수신자가 아닌 경우 예외 처리
        if(!friendship.getReceiver().getId().equals(currentUser.getId())) {
            throw new CommException(CommResponseStatus.ONLY_RECEIVER_CAN_PROCESS);
        }
        // 요청 상태가 REQUEST가 아닐 경우 예외 처리
        if(!friendship.getStatus().equals(FRIENDSHIP_STATUS.REQUESTED)) {
            throw new CommException(CommResponseStatus.REQUEST_STATUS_ERROR);
        }

        friendship.updateStatus(FRIENDSHIP_STATUS.REJECTED);
    }


    private Users getUserOrThrow(Long userId) {
        return usersJPARepository.findById(userId)
                .orElseThrow(() -> new CommException(CommResponseStatus.NOT_FOUND_USER));
    }
}
