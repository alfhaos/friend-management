package com.apr.aprbackendassignment.controller;

import com.apr.aprbackendassignment.common.request.PageRequestParam;
import com.apr.aprbackendassignment.common.response.CommResponse;
import com.apr.aprbackendassignment.common.response.PageResponse;
import com.apr.aprbackendassignment.model.dto.FriendshipDto;
import com.apr.aprbackendassignment.service.FriendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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

    private final FriendService friendService;

    @Operation(summary = "친구 목록 조회")
    @GetMapping
    public CommResponse<PageResponse<?>> getFriendsList( @ParameterObject PageRequestParam param) {

        Pageable pageable = param.toPageable();

        PageResponse<FriendshipDto> pageResponse = friendService.getFriendsList(pageable);
        return new CommResponse<>(pageResponse);
    }


}
