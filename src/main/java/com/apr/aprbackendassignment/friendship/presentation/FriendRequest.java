package com.apr.aprbackendassignment.friendship.presentation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 친구 신청 대상 사용자 입력. */

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendRequest {

    @NotNull
    @Positive
    private Long targetUserId;
}
