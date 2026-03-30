package com.apr.aprbackendassignment.service.impl;

import com.apr.aprbackendassignment.common.exception.CommException;
import com.apr.aprbackendassignment.common.request.PageRequestParam;
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
import com.apr.aprbackendassignment.util.FriendRequestFacade;
import com.apr.aprbackendassignment.util.LimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * =====================================================
 * Class Name   : FriendServiceTest
 * Description  :
 *  - 친구 관계 관리를 위한 서비스 구현 클래스 테스트

 * 주요 기능
 *  - FriendService 클래스의 메서드 테스트
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
    private LimitProperties limitProperties;

    @Autowired
    private FriendService friendService;

    @Autowired
    private FriendRequestFacade friendRequestFacade;

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

        PageResponse<FriendsResponse> response = friendService.getFriendsList(pageable);

        assertThat(response).isNotNull();
        assertThat(response.getTotalPages()).isGreaterThanOrEqualTo(1);
        assertThat(response.getTotalCount()).isGreaterThanOrEqualTo(0);
        assertThat(response.getItems()).isNotNull();
    }


    // 기능 요구 사항 2 : 받은 친구 신청 목록 조회 테스트 코드
    @Test
    @Transactional
    void getFriendsReceiveList() {
        PageRequestParam param = new PageRequestParam(0, 30, "requestedAt,desc");
        Pageable pageable = param.toPageable();
        WINDOW_SLIDING windowSliding = WINDOW_SLIDING.DAY;

        PageResponse<FriendsRequestsResponse> response =  friendService.getReceiveFriendsList(pageable, windowSliding);

        assertThat(response).isNotNull();
        assertThat(response.getTotalPages()).isGreaterThanOrEqualTo(1);
        assertThat(response.getTotalCount()).isGreaterThanOrEqualTo(0);
        assertThat(response.getItems()).isNotNull();

    }
    // 기능 요구 사항 3 : 친구 신청 테스트 코드
    @Test
    @Transactional
    void requestFriend() {

        FriendRequest friendRequest = new FriendRequest(4L);

        friendRequestFacade.requestFriend(CURRENT_USER_ID, friendRequest);

        Friendship friendship = friendshipJPARepository.findByRequesterIdAndReceiverId(CURRENT_USER_ID, friendRequest.getTargetUserId());

        assertThat(friendship.getStatus()).isEqualTo(FRIENDSHIP_STATUS.REQUESTED);
        assertThat(friendship.getRequester().getId()).isEqualTo(CURRENT_USER_ID);
        assertThat(friendship.getReceiver().getId()).isEqualTo(friendRequest.getTargetUserId());
    }

    // 기능 요구 사항 3-1 : 친구 신청 거절 테스트 코드
    @Test
    @Transactional
    void rejectedRequestFriend() {
        // 테이블의 request-id 값 입력
        String requestId = "f5143ce5-9bf7-459e-8d65-7b04a4c7071b";

        friendService.requestReject(CURRENT_USER_ID, requestId);

        Friendship friendship = friendshipJPARepository.findById(UUID.fromString(requestId))
                .orElseThrow(() -> new CommException(CommResponseStatus.NOT_FOUND_FRIENDSHIP));

        assertThat(friendship.getStatus()).isEqualTo(FRIENDSHIP_STATUS.REJECTED);
    }

    // 기능 요구 사항 3-2 : 요청 제한 테스트 코드
    @Test
    @Transactional
    void rateLimit_test() throws Exception {
        String url = "/api/friends/request";

        // 10번 정상 요청
        for (int i = 0; i < 10; i++) {

            FriendRequest request =
                    new FriendRequest((long) i + 2); // 매번 다른 사용자

            String body = objectMapper.writeValueAsString(request);

            mockMvc.perform(
                            post(url)
                                        .header("X-USER-ID", CURRENT_USER_ID)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body)
                    ).andExpect(status().isOk());
        }

        // 11번째 요청 -> 429
        FriendRequest lastRequest =
                new FriendRequest(100L);

        String lastBody = objectMapper.writeValueAsString(lastRequest);
        mockMvc.perform(
                        post(url)
                                .header("X-USER-ID", 1L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(lastBody)
                )
                .andExpect(status().isTooManyRequests());
    }

    // 기능 요구 사항 3-3 : 친구 신청 동시성 제어
    @Test
    @Transactional
    void requestFriend_concurrentOnlyOneRequestCreated() throws Exception {
        // Given
        Long userA = 1L;
        FriendRequest friendRequestA = new FriendRequest(2L);
        Long userB = 2L;
        FriendRequest friendRequestB = new FriendRequest(1L);
        // 두 작업을 동시에 실행하기 위한 준비
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);

        // When
        // 스레드 1: A -> B 신청
        executorService.submit(() -> {
            try {
                friendRequestFacade.requestFriend(userA, friendRequestA);
            } catch (Exception e) {
                System.out.println("A->B 요청 실패: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        // 스레드 2: B -> A 신청
        executorService.submit(() -> {
            try {
                friendRequestFacade.requestFriend(userB, friendRequestB);
            } catch (Exception e) {
                System.out.println("B->A 요청 실패: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        latch.await(); // 두 요청이 모두 끝날 때까지 대기

        // Then
        // DB에는 둘 중 어느 쪽이든 '단 하나'의 레코드만 존재해야 함
        int result = friendshipJPARepository.checkConcurrentRequest(userA, FRIENDSHIP_STATUS.REQUESTED);
        assertThat(result).isEqualTo(1);
    }

    // 기능 요구 사항 4 : 친구 수락 테스트 코드
    @Test
    @Transactional
    @Rollback(false)
    void requestAccept_test() throws Exception {
        // friendShip 테이블의 request-id 값 입력
        String requestId = "b1ea936b-7c78-4055-9015-7d72d6700c45";
        
        friendService.requestAccept(CURRENT_USER_ID, requestId);

        Friendship ckFriendship = friendshipJPARepository.findById(UUID.fromString(requestId))
                .orElseThrow(() -> new CommException(CommResponseStatus.NOT_FOUND_FRIENDSHIP));
        assertEquals(ckFriendship.getStatus(), FRIENDSHIP_STATUS.ACCEPTED, "친구 상태가 ACCEPTED가 아닙니다.");
    }

    // 기능 요구 사항 5 : 친구 요청 거절 테스트 코드
    @Test
    @Transactional
    @Rollback(false)
    void requestReject_test() throws Exception {
        // friendShip 테이블의 request-id 값 입력
        String requestId = "737d4034-5b13-43c0-9be2-50af7fc27cb3";
        Long xUserId = CURRENT_USER_ID;

        friendService.requestReject(xUserId, requestId);
        
        Friendship ckFriendship = friendshipJPARepository.findById(UUID.fromString(requestId))
                .orElseThrow(() -> new CommException(CommResponseStatus.NOT_FOUND_FRIENDSHIP));
        assertEquals(ckFriendship.getStatus(), FRIENDSHIP_STATUS.REJECTED, "친구 상태가 REJECTE가 아닙니다.");
    }

}
