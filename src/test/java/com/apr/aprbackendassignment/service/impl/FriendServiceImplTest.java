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

        Users currentUser = usersJPARepository.findByIdOrThrow(CURRENT_USER_ID);
        Page<Friendship> result = friendshipJPARepository.getFriendsList(currentUser.getId(), FRIENDSHIP_STATUS.ACCEPTED, pageable);
        Page<FriendsResponse> dtoPage = FriendsResponse.fromEntityPage(result, currentUser.getId());

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

        Users currentUser = usersJPARepository.findById(CURRENT_USER_ID)
                .orElseThrow(() -> new CommException(CommResponseStatus.NOT_FOUND_USER));


        LocalDateTime time = windowSliding.calculateTimeWindow();
        LocalDateTime start = null;
        LocalDateTime end = null;

        // over일 경우 null로 넘긴다.
        if (time != null) {
            LocalDate date = time.toLocalDate();
            start = date.atStartOfDay();
            end = date.plusDays(1).atStartOfDay();
        }
        Page<Friendship> result = friendshipJPARepository.getFriendsReceiveList(currentUser.getId(), start, end, pageable);
        Page<FriendsRequestsResponse> dtoPage =  FriendsRequestsResponse.fromEntityPage(result);
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

        Users currentUser = usersJPARepository.findById(CURRENT_USER_ID)
                .orElseThrow(() -> new CommException(CommResponseStatus.NOT_FOUND_USER));

        FriendRequest friendRequest = new FriendRequest(4L);

        // 이미 받은 요청이 있는지 확인
        Friendship existingRequest = friendshipJPARepository.checkExistingRequest(currentUser.getId(),friendRequest.getTargetUserId());
        if (existingRequest != null) {
            throw new CommException(CommResponseStatus.ALREADY_RREQUESTED_FRIENDSHIP);
        }

        // 조회했을떄 존재하지 않는 상대방일 경우 예외 처리
        Users targetUser = usersJPARepository.findById(friendRequest.getTargetUserId())
                .orElseThrow(() -> new CommException(CommResponseStatus.NOT_FOUND_USER));
        Long targetUserId = targetUser.getId();

        // 자기자신에게 친구 요청 보낼 경우 예외 처리
        if(currentUser.getId().equals(targetUserId)) {
            throw new CommException(CommResponseStatus.SELF_FRIEND_REQUEST);
        }
        // 거절시 재요청 가능 요구사항
        Friendship friendship = friendshipJPARepository.findByRequesterIdAndReceiverId(currentUser.getId(), targetUser.getId());

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

    // 기능 요구 사항 3-1 : 친구 신청 거절 테스트 코드
    @Test
    @Transactional
    @Rollback(false)
    void rejectedRequestFriend() {
        // 테이블의 request-id 값 입력
        String requestId = "f5143ce5-9bf7-459e-8d65-7b04a4c7071b";
        Friendship friendship = friendshipJPARepository.findById(UUID.fromString(requestId))
                .orElseThrow(() -> new CommException(CommResponseStatus.NOT_FOUND_FRIENDSHIP));

        assertEquals(String.valueOf(friendship.getId()), requestId,"아이디가 불일치 합니다.");

        friendship.updateStatus(FRIENDSHIP_STATUS.REJECTED);
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

    // 기능 요구 사항 3-3 : 친구 신청 동시성 제어
    @Test
    @Transactional
    @DisplayName("A가 B에게, B가 A에게 동시에 신청해도 하나만 성공")
    @Rollback(false)
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
        Long xUserId = CURRENT_USER_ID;

        Users currentUser = usersJPARepository.findById(xUserId)
                .orElseThrow(() -> new CommException(CommResponseStatus.NOT_FOUND_USER));

        Friendship friendship = friendshipJPARepository.findById(UUID.fromString(requestId))
                .orElseThrow(() -> new CommException(CommResponseStatus.NOT_FOUND_FRIENDSHIP));

        // 수신자가 아닌 경우 예외 처리
        if(friendship.getReceiver() != currentUser) {
            throw new CommException(CommResponseStatus.ONLY_RECEIVER_CAN_PROCESS);
        }

        int currentUserFriendCnt = friendshipJPARepository.countUsersFriends(currentUser.getId(), FRIENDSHIP_STATUS.ACCEPTED);

        // 현재 로그인 유저의 친구 수 체크
        if(currentUserFriendCnt >= limitProperties.getMaxFriend()) {
            throw new CommException(CommResponseStatus.FRIEND_LIMIT_EXCEEDED_FOR_ACCEPTOR);
        }
        // 요청 상태가 REQUEST가 아닐 경우 예외 처리
        if(!friendship.getStatus().equals(FRIENDSHIP_STATUS.REQUESTED)) {
            throw new CommException(CommResponseStatus.REQUEST_STATUS_ERROR);
        }

        friendship.updateStatus(FRIENDSHIP_STATUS.ACCEPTED);

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

        Users currentUser = usersJPARepository.findById(xUserId)
                .orElseThrow(() -> new CommException(CommResponseStatus.NOT_FOUND_USER));

        Friendship friendship = friendshipJPARepository.findById(UUID.fromString(requestId))
                .orElseThrow(() -> new CommException(CommResponseStatus.NOT_FOUND_FRIENDSHIP));

        // 수신자가 아닌 경우 예외 처리
        if(friendship.getReceiver() != currentUser) {
            throw new CommException(CommResponseStatus.ONLY_RECEIVER_CAN_PROCESS);
        }

        // 요청 상태가 REQUEST가 아닐 경우 예외 처리
        if(!friendship.getStatus().equals(FRIENDSHIP_STATUS.REQUESTED)) {
            throw new CommException(CommResponseStatus.REQUEST_STATUS_ERROR);
        }

        friendship.updateStatus(FRIENDSHIP_STATUS.REJECTED);
    }

}
