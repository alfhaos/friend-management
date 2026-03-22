package com.apr.aprbackendassignment.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * =====================================================
 * Class Name   : FriendRequestDto
 * Description  :
 *  - 사용자 데이터를 계층간 이동을 위한 DTO 클래스
 *
 * 주요 기능
 *  - 엔티티-DTO 변환: fromEntityPage 메서드를 통해 Friendship 엔티티를 FriendsRequestsResponse 변환
 * =====================================================
 */

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendRequest {

    @NotNull
    @Positive
    private Long targetUserId;
}
