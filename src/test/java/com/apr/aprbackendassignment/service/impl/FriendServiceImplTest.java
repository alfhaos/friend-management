package com.apr.aprbackendassignment.service.impl;

import com.apr.aprbackendassignment.common.request.PageRequestParam;
import com.apr.aprbackendassignment.common.response.PageResponse;
import com.apr.aprbackendassignment.model.constant.FRIENDSHIP_STATUS;
import com.apr.aprbackendassignment.model.constant.WINDOW_SLIDING;
import com.apr.aprbackendassignment.model.dto.UsersDto;
import com.apr.aprbackendassignment.model.dto.response.FriendsRequestsResponse;
import com.apr.aprbackendassignment.model.dto.response.FriendsResponse;
import com.apr.aprbackendassignment.model.entity.Friendship;
import com.apr.aprbackendassignment.model.entity.Users;
import com.apr.aprbackendassignment.repository.FriendRepository;
import com.apr.aprbackendassignment.repository.jpaRepository.FriendshipJPARepository;
import com.apr.aprbackendassignment.repository.jpaRepository.UsersJPARepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * =====================================================
 * Class Name   : FriendServiceImplTest
 * Description  :
 *  - 친구 관계 관리를 위한 서비스 구현 클래스 테스트
 *
 * 주요 기능
 *  - FriendServiceImpl 클래스의 메서드 테스트
 * =====================================================
 */
@SpringBootTest
@Slf4j
class FriendServiceImplTest {

    @Autowired
    private FriendRepository friendRepository;

    @Autowired
    private UsersJPARepository usersJPARepository;
    @Autowired
    private FriendshipJPARepository friendshipJPARepository;

    private static final String UserName = "user";
    private static final Long CURRENT_USER_ID = 1L;

    @Test
    @Transactional
    @Rollback(false)
    void makeMillionUsers() {
        friendRepository.makeMillionUsers(UserName);
        log.info("Made milion users with base name: {}", UserName);

        int userCount = friendRepository.selectAllUsersLength();
        assertTrue(userCount >= 10000, "사용자 수는 10000 이상이어야 합니다");
    }

    /*
       샘플 친구관계 생성 테스트 코드
       사용자(1L)를 기준으로 10,000개의 사용자에 대해서 친구관계를 생성한다
        5개의 구간으로 나누어 각각 다른 날짜로 친구 신청일자를 설정한다
    */
    @Test
    @Transactional
    @Rollback(false)
    void makeSampleFriendShip() {

        Users currentUser = usersJPARepository.findByIdOrThrow(CURRENT_USER_ID);
        List<Users> allUsers = usersJPARepository.findAll();

        List<Friendship> friendshipList = new ArrayList<>();

        int total = allUsers.size() - 1; // 자기 자신 제외
        int chunk = total / 5;

        int index = 0;

        for (Users user : allUsers) {
            if (user.equals(currentUser)) continue;

            Friendship friendship =
                    Friendship.create(user, currentUser,
                            FRIENDSHIP_STATUS.REQUESTED);
            // 분기
            LocalDateTime baseTime;
            if (index < chunk) {
                baseTime = LocalDateTime.now().minusDays(WINDOW_SLIDING.DAY.getCodeDate()); // 1d
            } else if (index < chunk * 2) {
                baseTime = LocalDateTime.now().minusDays(WINDOW_SLIDING.WEEK.getCodeDate()); // 7d
            } else if (index < chunk * 3) {
                baseTime = LocalDateTime.now().minusDays(WINDOW_SLIDING.MONTH.getCodeDate()); // 30d
            } else if (index < chunk * 4) {
                baseTime = LocalDateTime.now().minusDays(WINDOW_SLIDING.NINETY_DAY.getCodeDate()); // 90d
            } else {
                baseTime = LocalDateTime.now().minusDays(WINDOW_SLIDING.OVER.getCodeDate()); // over
            }

            friendship.updateRequestedAt(baseTime);
            friendshipList.add(friendship);
            index++;
        }

        friendshipJPARepository.saveAll(friendshipList);

        int friendshipCount =
                friendshipJPARepository.findAll().size();

        assertTrue(friendshipCount >= 9999,
                "친구 관계 수는 9999 이상 이어야 합니다");
    }
    // 기능 요구 사항 1 : 친구 목록 조회 테스트 코드
    @Test
    @Transactional
    void getFriendsList() {
        PageRequestParam param = new PageRequestParam(1, 5, "approvedAt,desc");
        Pageable pageable = param.toPageable();

        Users currentUser = usersJPARepository.findByIdOrThrow(CURRENT_USER_ID);
        UsersDto currentUserDto = UsersDto.fromEntity(currentUser);

        Page<FriendsResponse> dtoPage = friendRepository.getFriendsList(currentUserDto.getId(), pageable);


        PageResponse<FriendsResponse> response =  new PageResponse<>(
                dtoPage.getTotalPages(),
                (int) dtoPage.getTotalElements(),
                dtoPage.getContent()
        );

        log.info("pageResponse getTotalCount : {}", response.getTotalCount());
        log.info("pageResponse getTotalPages : {}", response.getTotalPages());
    }


    // 기능 요구 사항 2 : 받은 친구 신청 목록 조회 테스트 코드
    @Test
    @Transactional
    void getFriendsReceiveList() {
        PageRequestParam param = new PageRequestParam(0, 30, "requestedAt,desc");
        Pageable pageable = param.toPageable();
        WINDOW_SLIDING windowSliding = WINDOW_SLIDING.DAY;

        UsersDto currentUserDto = UsersDto.fromEntity(usersJPARepository.findByIdOrThrow(CURRENT_USER_ID));
        Page<FriendsRequestsResponse> dtoPage = friendRepository.getFriendsReceiveList(currentUserDto.getId(), pageable, windowSliding);
        PageResponse<FriendsRequestsResponse> response =  new PageResponse<>(
                dtoPage.getTotalPages(),
                (int) dtoPage.getTotalElements(),
                dtoPage.getContent()
        );
        log.info("pageResponse getTotalCount : {}", response.getTotalCount());
        log.info("pageResponse getTotalPages : {}", response.getTotalPages());

    }

}