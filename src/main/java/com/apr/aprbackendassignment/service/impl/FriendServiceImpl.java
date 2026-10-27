package com.apr.aprbackendassignment.service.impl;

import com.apr.aprbackendassignment.common.exception.FriendshipError;
import com.apr.aprbackendassignment.common.exception.FriendshipException;
import com.apr.aprbackendassignment.common.response.PageResponse;
import com.apr.aprbackendassignment.model.constant.FRIENDSHIP_STATUS;
import com.apr.aprbackendassignment.model.constant.WINDOW_SLIDING;
import com.apr.aprbackendassignment.model.dto.response.FriendsRequestsResponse;
import com.apr.aprbackendassignment.model.dto.response.FriendsResponse;
import com.apr.aprbackendassignment.model.entity.Friendship;
import com.apr.aprbackendassignment.model.entity.Users;
import com.apr.aprbackendassignment.repository.FriendshipJPARepository;
import com.apr.aprbackendassignment.repository.UsersJPARepository;
import com.apr.aprbackendassignment.service.FriendService;
import com.apr.aprbackendassignment.util.FriendLimitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
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
    private final FriendLimitProperties friendLimitProperties;
    private final Clock clock;
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

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime time = windowSliding.calculateTimeWindow(now);
        LocalDateTime start = time != null ? time.toLocalDate().atStartOfDay() : null;
        LocalDateTime end = time != null ? now : null;

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
    public void requestFriend(Long xUserId, Long targetUserId) {
        if (xUserId.equals(targetUserId)) {
            throw new FriendshipException(FriendshipError.SELF_FRIEND_REQUEST);
        }

        Users[] lockedUsers = lockUsers(xUserId, targetUserId);
        Users currentUser = xUserId.equals(lockedUsers[0].getId()) ? lockedUsers[0] : lockedUsers[1];
        Users targetUser = targetUserId.equals(lockedUsers[0].getId()) ? lockedUsers[0] : lockedUsers[1];
        List<Friendship> relationships = friendshipJPARepository.findBetweenUsers(xUserId, targetUserId);

        for (Friendship relationship : relationships) {
            if (relationship.getStatus() == FRIENDSHIP_STATUS.REQUESTED) {
                throw new FriendshipException(FriendshipError.ALREADY_RREQUESTED_FRIENDSHIP);
            }
            if (relationship.getStatus() == FRIENDSHIP_STATUS.ACCEPTED) {
                throw new FriendshipException(FriendshipError.ALREADY_REQUESTED_FRIEND);
            }
        }

        for (Friendship relationship : relationships) {
            if (relationship.getRequester().getId().equals(xUserId)
                    && relationship.getStatus() == FRIENDSHIP_STATUS.REJECTED) {
                relationship.requestAgain(xUserId, LocalDateTime.now(clock));
                return;
            }
        }

        friendshipJPARepository.save(Friendship.request(currentUser, targetUser, LocalDateTime.now(clock)));
    }

    @Transactional
    @Override
    public void requestAccept(Long xUserId, UUID requestId) {
        Friendship friendship = findLockedRequest(xUserId, requestId);

        // 수신자가 아닌 경우 예외 처리
        if (!friendship.getReceiver().getId().equals(xUserId)) {
            throw new FriendshipException(FriendshipError.ONLY_RECEIVER_CAN_PROCESS);
        }

        if (friendship.getStatus() != FRIENDSHIP_STATUS.REQUESTED) {
            throw new FriendshipException(FriendshipError.REQUEST_STATUS_ERROR);
        }

        Long targetUserId = friendship.getRequester().getId();

        int currentUserFriendCnt = friendshipJPARepository.countUsersFriends(xUserId, FRIENDSHIP_STATUS.ACCEPTED);

        // 현재 로그인 유저의 친구 수 체크
        if(currentUserFriendCnt >= friendLimitProperties.getMaxFriend()) {
            throw new FriendshipException(FriendshipError.FRIEND_LIMIT_EXCEEDED_FOR_ACCEPTOR);
        }

        // 상대방 친구 수 제한 체크
        int targetUserFriendCnt = friendshipJPARepository.countUsersFriends(targetUserId, FRIENDSHIP_STATUS.ACCEPTED);
        if(targetUserFriendCnt >= friendLimitProperties.getMaxFriend()) {
            throw new FriendshipException(FriendshipError.FRIEND_LIMIT_EXCEEDED_REQUESTER);
        }
        friendship.accept(xUserId, LocalDateTime.now(clock));
    }

    @Transactional
    @Override
    public void requestReject(Long xUserId, UUID requestId) {
        Friendship friendship = findLockedRequest(xUserId, requestId);

        // 수신자가 아닌 경우 예외 처리
        if (!friendship.getReceiver().getId().equals(xUserId)) {
            throw new FriendshipException(FriendshipError.ONLY_RECEIVER_CAN_PROCESS);
        }
        // 요청 상태가 REQUEST가 아닐 경우 예외 처리
        if(!friendship.getStatus().equals(FRIENDSHIP_STATUS.REQUESTED)) {
            throw new FriendshipException(FriendshipError.REQUEST_STATUS_ERROR);
        }

        friendship.reject(xUserId);
    }


    private Users getUserOrThrow(Long userId) {
        return usersJPARepository.findById(userId)
                .orElseThrow(() -> new FriendshipException(FriendshipError.NOT_FOUND_USER));
    }

    private Friendship findLockedRequest(Long currentUserId, UUID requestId) {
        getUserOrThrow(currentUserId);
        FriendshipJPARepository.ParticipantIds participants = friendshipJPARepository.findParticipantIds(requestId)
                .orElseThrow(() -> new FriendshipException(FriendshipError.NOT_FOUND_FRIENDSHIP));
        if (!participants.getReceiverId().equals(currentUserId)) {
            throw new FriendshipException(FriendshipError.ONLY_RECEIVER_CAN_PROCESS);
        }
        lockUsers(participants.getRequesterId(), participants.getReceiverId());
        return friendshipJPARepository.findById(requestId)
                .orElseThrow(() -> new FriendshipException(FriendshipError.NOT_FOUND_FRIENDSHIP));
    }

    private Users[] lockUsers(Long firstUserId, Long secondUserId) {
        Long lowerId = Math.min(firstUserId, secondUserId);
        Long upperId = Math.max(firstUserId, secondUserId);
        Users lower = usersJPARepository.findLockedById(lowerId)
                .orElseThrow(() -> new FriendshipException(FriendshipError.NOT_FOUND_USER));
        Users upper = usersJPARepository.findLockedById(upperId)
                .orElseThrow(() -> new FriendshipException(FriendshipError.NOT_FOUND_USER));
        return new Users[] {lower, upper};
    }
}
