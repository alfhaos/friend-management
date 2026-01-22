package com.apr.aprbackendassignment.repository;

import com.apr.aprbackendassignment.common.exception.CommException;
import com.apr.aprbackendassignment.common.response.CommResponseStatus;
import com.apr.aprbackendassignment.model.constant.FRIENDSHIP_STATUS;
import com.apr.aprbackendassignment.model.constant.WINDOW_SLIDING;
import com.apr.aprbackendassignment.model.dto.UsersDto;
import com.apr.aprbackendassignment.model.dto.response.FriendsRequestsResponse;
import com.apr.aprbackendassignment.model.dto.response.FriendsResponse;
import com.apr.aprbackendassignment.model.entity.Friendship;
import com.apr.aprbackendassignment.model.entity.Users;
import com.apr.aprbackendassignment.repository.jpaRepository.FriendshipJPARepository;
import com.apr.aprbackendassignment.repository.jpaRepository.UsersJPARepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * =====================================================
 * Class Name   : FriendRepository
 * Description  :
 *  - 친구 관계 관리를 위한 리포지토리 클래스
 *
 * 주요 기능
 *  - 친구 관계 데이터 접근 및 조작을 위한 메서드 제공
 * =====================================================
 */
@Repository
@RequiredArgsConstructor
public class FriendRepository {

    private final FriendshipJPARepository friendshipJPARepository;
    private final UsersJPARepository usersJPARepository;

    public int selectAllUsersLength() {
        return usersJPARepository.findAll().size();
    }

    public void makeMillionUsers(String userName) {
        for (long i = 1; i <= 10000; i++) {
            Users user = Users.create(userName + i);
            usersJPARepository.save(user);
        }
    }

    public Page<FriendsResponse> getFriendsList(Long currentUserId, Pageable pageable) {
        Page<Friendship> result = friendshipJPARepository.getFriendsList(currentUserId, FRIENDSHIP_STATUS.ACCEPTED, pageable);
        return FriendsResponse.fromEntityPage(result, currentUserId);
    }

    public UsersDto findUserById(Long currentUserId) {
        Users currentUser = usersJPARepository.findByIdOrThrow(currentUserId);
        return UsersDto.fromEntity(currentUser);
    }

    public List<UsersDto> selectAllUsers() {
        List<Users> usersList = usersJPARepository.findAll();
        return UsersDto.fromEntityList(usersList);
    }

    public Page<FriendsRequestsResponse> getFriendsReceiveList(Long currentUserId, Pageable pageable, WINDOW_SLIDING windowSliding) {
        LocalDateTime time = windowSliding.calculateTimeWindow();
        LocalDateTime start = null;
        LocalDateTime end = null;

        // over일 경우 null로 넘긴다.
        if (time != null) {
            LocalDate date = time.toLocalDate();
            start = date.atStartOfDay();
            end = date.plusDays(1).atStartOfDay();
        }

        Page<Friendship> result = friendshipJPARepository.getFriendsReceiveList(currentUserId, start, end, pageable);
        return FriendsRequestsResponse.fromEntityPage(result);
    }

    public boolean existsFriendRequest(Long xUserId, Long targetUserId) {
        return friendshipJPARepository.existsFriendRequest(xUserId, targetUserId, FRIENDSHIP_STATUS.REJECTED);
    }

    public Friendship findFriendRequest(Long xUserId, Long targetUserId) {
        Friendship friendship = friendshipJPARepository.findFriendRequest(xUserId, targetUserId);

        // 거절 내역이 있다면 재요청을 위해 반환
        // 그 외에는 이미 요청된 상태이므로 예외 처리
        if(friendship != null) {
            if(friendship.getStatus().equals(FRIENDSHIP_STATUS.REJECTED)) {
                return friendship;
            } else {
                throw new CommException(CommResponseStatus.ALREADY_REQUESTED_FRIEND);
            }
        }
        return null;
    }

    public Users findUserByIdOrThrow(Long xUserId) {
        return usersJPARepository.findByIdOrThrow(xUserId);
    }

    public void requestFriend(Users currentUser, Users targetUser) {
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
