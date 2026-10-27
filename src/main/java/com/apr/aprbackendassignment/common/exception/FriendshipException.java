package com.apr.aprbackendassignment.common.exception;

import lombok.Getter;

/**
 * 친구 기능의 업무 실패를 전달하는 HTTP 비의존 예외다.
 */
@Getter
public class FriendshipException extends RuntimeException {

    private final FriendshipError error;

    public FriendshipException(FriendshipError error) {
        super(error.name());
        this.error = error;
    }
}
