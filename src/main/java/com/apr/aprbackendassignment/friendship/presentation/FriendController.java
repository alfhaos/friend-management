package com.apr.aprbackendassignment.friendship.presentation;

import com.apr.aprbackendassignment.common.response.CommResponse;
import com.apr.aprbackendassignment.common.response.CommResponseStatus;
import com.apr.aprbackendassignment.common.response.PageResponse;
import com.apr.aprbackendassignment.friendship.application.FriendRequestFacade;
import com.apr.aprbackendassignment.friendship.application.FriendService;
import com.apr.aprbackendassignment.friendship.application.FriendsRequestsResponse;
import com.apr.aprbackendassignment.friendship.application.FriendsResponse;
import com.apr.aprbackendassignment.friendship.application.RequestWindow;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * =====================================================
 * Class Name   : FriendController
 * Description  :
 *  - 친구 관련 API를 처리 하는 컨트롤러 클래스
 *
 * 주요 기능
 *  - 친구 신청, 수락, 거절, 목록 조회 기능을 제공
 * =====================================================
 */

@Tag(name = "Friend API")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/friends")
@Validated
@Slf4j
public class FriendController {

    private static final String xUserIdHeader = "X-user-Id";
    private static final String requestIdPath = "requestId";
    private final FriendService friendService;
    private final FriendRequestFacade friendRequestFacade;

    @Operation(summary = "친구 목록 조회")
    @GetMapping
    public CommResponse<PageResponse<?>> getFriendsList(@Valid @ParameterObject PageRequestParam param) {

        Pageable pageable = param.toPageable();

        PageResponse<FriendsResponse> pageResponse = friendService.getFriendsList(pageable);

        return CommResponse.success(pageResponse);
    }
    @Operation(summary = "받은 친구 신청 목록 조회")
    @GetMapping("/requests")
    public CommResponse<PageResponse<?>> getReceiveFriendsList(@Valid @ParameterObject PageRequestParam param
            , @RequestParam("window") String windowParam) {

        Pageable pageable = param.toPageable();
        RequestWindow requestWindow = RequestWindow.from(windowParam);

        PageResponse<FriendsRequestsResponse> pageResponse = friendService.getReceiveFriendsList(pageable, requestWindow);
        return CommResponse.success(pageResponse);
    }
    @Operation(summary = "친구 신청")
    @PostMapping("/request")
    public CommResponse<CommResponseStatus> requestFriend(
            @Positive @RequestHeader(xUserIdHeader) Long xUserId,
            @Valid @RequestBody FriendRequest friendRequest) {

        friendRequestFacade.requestFriend(xUserId, friendRequest.getTargetUserId());
        return CommResponse.success();
    }
    @Operation(summary = "친구 신청 수락")
    @PostMapping("/accept/{requestId}")
    public CommResponse<CommResponseStatus> requestAccept(
            @Positive @RequestHeader(xUserIdHeader) Long xUserId,
            @PathVariable(requestIdPath) UUID requestId){

        friendService.requestAccept(xUserId, requestId);
        return CommResponse.success();
    }

    @Operation(summary = "친구 신청 거절")
    @PostMapping("/reject/{requestId}")
    public CommResponse<CommResponseStatus> requestReject(
            @Positive @RequestHeader(xUserIdHeader) Long xUserId,
            @PathVariable(requestIdPath) UUID requestId){

        friendService.requestReject(xUserId, requestId);
        return CommResponse.success();
    }
}
