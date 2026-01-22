package com.apr.aprbackendassignment.service.impl;

import com.apr.aprbackendassignment.common.exception.CommException;
import com.apr.aprbackendassignment.common.request.PageRequestParam;
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
import com.apr.aprbackendassignment.repository.jpaRepository.FriendshipJPARepository;
import com.apr.aprbackendassignment.repository.jpaRepository.UsersJPARepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * =====================================================
 * Class Name   : FriendServiceImplTest
 * Description  :
 *  - 친구 관계 관리를 위한 서비스 구현 클래스 테스트

 * 주요 기능
 *  - FriendServiceImpl 클래스의 메서드 테스트
 * =====================================================
 */
@SpringBootTest
@Slf4j
@AutoConfigureMockMvc
class FriendServiceImplTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FriendRepository friendRepository;

    @Autowired
    private UsersJPARepository usersJPARepository;
    @Autowired
    private FriendshipJPARepository friendshipJPARepository;
    private static final Long CURRENT_USER_ID = 1L;

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
    // 기능 요구 사항 3 : 친구 신청 테스트 코드
    @Test
    @Transactional
    @Rollback(false)
    void requestFriend() {
        Long xUserId = CURRENT_USER_ID;
        Users currentUser = usersJPARepository.findByIdOrThrow(xUserId);
        FriendRequest friendRequest = new FriendRequest(2L);

        // 자기자신에게 친구 요청 보낼 경우 예외 처리
        if(xUserId.equals(friendRequest.getTargetUserId())) {
            throw new CommException(CommResponseStatus.SELF_FRIEND_REQUEST);
        }

        // 조회했을떄 존재하지 않는 상대방일 경우 예외 처리
        Users targetUser = usersJPARepository.findByIdOrThrow(friendRequest.getTargetUserId());


        // 거절시 재요청 가능 요구사항
        // 기존에 친구 요청이 존재하는지 확인 및 만약 거절 이력이 있을경우 해당 데이터 상태를 REQUESTED로 변경
        Friendship friendship = friendRepository.findFriendRequest(xUserId, targetUser.getId());

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

    // 기능 요구 사항 3-1 : 친구 신청 거절 테스트 코드
    @Test
    @Transactional
    @Rollback(false)
    void rejectedRequestFriend() {
        String requestId = "76e3848e-35a8-46c3-8f0b-a3181c9529a7";
        Friendship friendship = friendshipJPARepository.findByIdOrThrow(requestId);

        assertEquals(String.valueOf(friendship.getId()), requestId,"아이디가 불일치 합니다.");

        friendship.updateStatus(FRIENDSHIP_STATUS.REJECTED);
    }

    // 기능 요구 사항 3-2 : 요청 제한 테스트 코드
    @Test
    @Transactional
    @Rollback(false)
    void rateLimit_test() throws Exception {
        String url = "/api/friends/request";

        // 10번 정상 요청
        for (int i = 0; i < 10; i++) {

            FriendRequest request =
                    new FriendRequest((long) i + 2); // 매번 다른 사용자

            String body = objectMapper.writeValueAsString(request);

            mockMvc.perform(
                            post(url)
                                    .header("X-USER-ID", 1L)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body)
                    ).andExpect(status().isOk());
        }

        // 11번째 요청 -> 429
        FriendRequest lastRequest =
                new FriendRequest(100L);

        String lastBody =
                objectMapper.writeValueAsString(lastRequest);
        mockMvc.perform(
                        post(url)
                                .header("X-USER-ID", 1L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(lastBody)
                )
                .andExpect(status().isTooManyRequests());
    }
}