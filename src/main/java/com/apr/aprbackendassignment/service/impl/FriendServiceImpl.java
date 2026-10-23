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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
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
        Long targetUserId = friendRequest.getTargetUserId();
        if (xUserId.equals(targetUserId)) {
            throw new CommException(CommResponseStatus.SELF_FRIEND_REQUEST);
        }

        Users[] lockedUsers = lockUsers(xUserId, targetUserId);
        Users currentUser = xUserId.equals(lockedUsers[0].getId()) ? lockedUsers[0] : lockedUsers[1];
        Users targetUser = targetUserId.equals(lockedUsers[0].getId()) ? lockedUsers[0] : lockedUsers[1];
        List<Friendship> relationships = friendshipJPARepository.findBetweenUsers(xUserId, targetUserId);

        for (Friendship relationship : relationships) {
            if (relationship.getStatus() == FRIENDSHIP_STATUS.REQUESTED) {
                throw new CommException(CommResponseStatus.ALREADY_RREQUESTED_FRIENDSHIP);
            }
            if (relationship.getStatus() == FRIENDSHIP_STATUS.ACCEPTED) {
                throw new CommException(CommResponseStatus.ALREADY_REQUESTED_FRIEND);
            }
        }

        for (Friendship relationship : relationships) {
            if (relationship.getRequester().getId().equals(xUserId)
                    && relationship.getStatus() == FRIENDSHIP_STATUS.REJECTED) {
                LocalDateTime requestedAt = LocalDateTime.now();
                relationship.updateUpdateAt(requestedAt);
                relationship.updateRequestedAt(requestedAt);
                relationship.updateStatus(FRIENDSHIP_STATUS.REQUESTED);
                return;
            }
        }

        friendshipJPARepository.save(Friendship.create(currentUser, targetUser, FRIENDSHIP_STATUS.REQUESTED));
    }

    @Transactional
    @Override
    public void requestAccept(Long xUserId, String requestId) {
        Friendship friendship = findLockedRequest(xUserId, UUID.fromString(requestId));

        // 수신자가 아닌 경우 예외 처리
        if (!friendship.getReceiver().getId().equals(xUserId)) {
            throw new CommException(CommResponseStatus.ONLY_RECEIVER_CAN_PROCESS);
        }

        if (friendship.getStatus() != FRIENDSHIP_STATUS.REQUESTED) {
            throw new CommException(CommResponseStatus.REQUEST_STATUS_ERROR);
        }

        Long targetUserId = friendship.getRequester().getId();

        int currentUserFriendCnt = friendshipJPARepository.countUsersFriends(xUserId, FRIENDSHIP_STATUS.ACCEPTED);

        // 현재 로그인 유저의 친구 수 체크
        if(currentUserFriendCnt >= limitProperties.getMaxFriend()) {
            throw new CommException(CommResponseStatus.FRIEND_LIMIT_EXCEEDED_FOR_ACCEPTOR);
        }

        // 상대방 친구 수 제한 체크
        int targetUserFriendCnt = friendshipJPARepository.countUsersFriends(targetUserId, FRIENDSHIP_STATUS.ACCEPTED);
        if(targetUserFriendCnt >= limitProperties.getMaxFriend()) {
            throw new CommException(CommResponseStatus.FRIEND_LIMIT_EXCEEDED_REQUESTER);
        }
        friendship.updateStatus(FRIENDSHIP_STATUS.ACCEPTED);
    }

    @Transactional
    @Override
    public void requestReject(Long xUserId, String requestId) {
        Friendship friendship = findLockedRequest(xUserId, UUID.fromString(requestId));

        // 수신자가 아닌 경우 예외 처리
        if (!friendship.getReceiver().getId().equals(xUserId)) {
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

    private Friendship findLockedRequest(Long currentUserId, UUID requestId) {
        getUserOrThrow(currentUserId);
        FriendshipJPARepository.ParticipantIds participants = friendshipJPARepository.findParticipantIds(requestId)
                .orElseThrow(() -> new CommException(CommResponseStatus.NOT_FOUND_FRIENDSHIP));
        if (!participants.getReceiverId().equals(currentUserId)) {
            throw new CommException(CommResponseStatus.ONLY_RECEIVER_CAN_PROCESS);
        }
        lockUsers(participants.getRequesterId(), participants.getReceiverId());
        return friendshipJPARepository.findById(requestId)
                .orElseThrow(() -> new CommException(CommResponseStatus.NOT_FOUND_FRIENDSHIP));
    }

    private Users[] lockUsers(Long firstUserId, Long secondUserId) {
        Long lowerId = Math.min(firstUserId, secondUserId);
        Long upperId = Math.max(firstUserId, secondUserId);
        Users lower = usersJPARepository.findLockedById(lowerId)
                .orElseThrow(() -> new CommException(CommResponseStatus.NOT_FOUND_USER));
        Users upper = usersJPARepository.findLockedById(upperId)
                .orElseThrow(() -> new CommException(CommResponseStatus.NOT_FOUND_USER));
        return new Users[] {lower, upper};
    }
}
