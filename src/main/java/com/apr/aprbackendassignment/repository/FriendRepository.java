package com.apr.aprbackendassignment.repository;

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
        Page<Friendship> result = friendshipJPARepository.getFriendsList(currentUserId, pageable);
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

        Page<Friendship> result = friendshipJPARepository.getFriendsReceiveList(currentUserId, pageable
                , start
                , end);
        return FriendsRequestsResponse.fromEntityPage(result);
    }
}
