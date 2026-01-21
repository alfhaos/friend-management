package com.apr.aprbackendassignment.service.impl;

import com.apr.aprbackendassignment.common.request.PageRequestParam;
import com.apr.aprbackendassignment.common.response.PageResponse;
import com.apr.aprbackendassignment.model.dto.FriendshipDto;
import com.apr.aprbackendassignment.model.dto.UsersDto;
import com.apr.aprbackendassignment.model.entity.Friendship;
import com.apr.aprbackendassignment.model.entity.Users;
import com.apr.aprbackendassignment.repository.FriendRepository;
import com.apr.aprbackendassignment.repository.jpaRepository.UsersJPARepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

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

    private static final String UserName = "user";
    private static final Long CURRENT_USER_ID = 1L;

    @Test
    @Transactional
    @Rollback(false)
    void makeMillionUsers() {
        friendRepository.makeMillionUsers(UserName);
        log.info("Made milion users with base name: {}", UserName);

        int userCount = friendRepository.selectAllUsers();
        assertTrue(userCount >= 10000, "사용자 수는 10000 이상이어야 합니다");
    }

    // 기능 요구 사항 1 : 친구 목록 조회 테스트 코드
    @Test
    @Transactional
    void getFriendsList() {
        PageRequestParam param = new PageRequestParam(1, 5, "approvedAt,desc");
        Pageable pageable = param.toPageable();

        Users currentUser = usersJPARepository.findByIdOrThrow(CURRENT_USER_ID);
        UsersDto currentUserDto = UsersDto.fromEntity(currentUser);

        Page<FriendshipDto> dtoPage = friendRepository.getFriendsList(currentUserDto.getId(), pageable);


        PageResponse<FriendshipDto> response =  new PageResponse<>(
                dtoPage.getTotalPages(),
                (int) dtoPage.getTotalElements(),
                dtoPage.getContent()
        );

        log.info("pageResponse getTotalCount : {}", response.getTotalCount());
        log.info("pageResponse getTotalPages : {}", response.getTotalPages());
    }
}