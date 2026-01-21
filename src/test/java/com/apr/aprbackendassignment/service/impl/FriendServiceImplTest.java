package com.apr.aprbackendassignment.service.impl;

import com.apr.aprbackendassignment.repository.FriendRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

    private static final String UserName = "user";

    @Test
    @Transactional
    @Rollback(false)
    void makeMillionUsers() {
        friendRepository.makeMillionUsers(UserName);
        log.info("Made milion users with base name: {}", UserName);

        int userCount = friendRepository.selectAllUsers();
        assertTrue(userCount >= 10000, "사용자 수는 10000 이상이어야 합니다");
    }
}