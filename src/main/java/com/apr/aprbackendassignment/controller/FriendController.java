package com.apr.aprbackendassignment.controller;

import com.apr.aprbackendassignment.common.exception.CommException;
import com.apr.aprbackendassignment.common.request.PageRequestParam;
import com.apr.aprbackendassignment.common.response.CommResponse;
import com.apr.aprbackendassignment.common.response.CommResponseStatus;
import com.apr.aprbackendassignment.common.response.PageResponse;
import com.apr.aprbackendassignment.model.constant.WINDOW_SLIDING;
import com.apr.aprbackendassignment.model.dto.UsersDto;
import com.apr.aprbackendassignment.model.dto.request.FriendRequest;
import com.apr.aprbackendassignment.model.dto.response.FriendsRequestsResponse;
import com.apr.aprbackendassignment.model.dto.response.FriendsResponse;
import com.apr.aprbackendassignment.model.entity.Friendship;
import com.apr.aprbackendassignment.model.entity.Users;
import com.apr.aprbackendassignment.service.FriendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

/**
 * =====================================================
 * Class Name   : FriendController
 * Description  :
 *  - 친구 관련 API를 처리 하는 컨트롤러 클래스
 *
 * 주요 기능
 *  - 친구 추가, 삭제, 조회 등의 기능을 제공
 * =====================================================
 */

@Tag(name = "Friend API")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/friends")
@Slf4j
public class FriendController {

    private static final String xUserIdHeader = "X-user-Id";
    private final FriendService friendService;

    @Operation(summary = "친구 목록 조회")
    @GetMapping
    public CommResponse<PageResponse<?>> getFriendsList( @ParameterObject PageRequestParam param) {

        Pageable pageable = param.toPageable();

        PageResponse<FriendsResponse> pageResponse = friendService.getFriendsList(pageable);
        return CommResponse.success(pageResponse);
    }
    @Operation(summary = "받은 친구 신청 목록 조회")
    @GetMapping("/requests")
    public CommResponse<PageResponse<?>> getReceiveFriendsList(@ParameterObject PageRequestParam param
            , @RequestParam("window") String windowParam) {

        Pageable pageable = param.toPageable();
        WINDOW_SLIDING windowSliding = WINDOW_SLIDING.convertWindowSliding(windowParam);

        PageResponse<FriendsRequestsResponse> pageResponse = friendService.getReceiveFriendsList(pageable, windowSliding);
        return CommResponse.success(pageResponse);
    }
    @Operation(summary = "친구 신청")
    @PostMapping("/request")
    public CommResponse<CommResponseStatus> requestFriend(
            @RequestHeader(xUserIdHeader) Long xUserId,
            @RequestBody FriendRequest friendRequest) {

        friendService.requestFriend(xUserId, friendRequest);
        return CommResponse.success();
    }
    @Operation(summary = "친구 신청 수락")
    @PostMapping("/accept/{requestId}")
    public CommResponse<CommResponseStatus> requestAccept(
            @RequestHeader(xUserIdHeader) Long xUserId,
            @PathVariable("requestId") String requestId){

        friendService.requestAccept(xUserId, requestId);
        return CommResponse.success();
    }

    @Operation(summary = "친구 신청 거절")
    @PostMapping("/reject/{requestId}")
    public CommResponse<CommResponseStatus> requestReject(
            @RequestHeader(xUserIdHeader) Long xUserId,
            @PathVariable("requestId") String requestId){

        friendService.requestReject(xUserId, requestId);
        return CommResponse.success();
    }
}
