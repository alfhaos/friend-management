package com.apr.aprbackendassignment.friendship.presentation;

import com.apr.aprbackendassignment.common.exception.InvalidRequestException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * =====================================================
 * Class Name   : FriendSort
 * Description  :
 *  - 정렬 그룹을 정의하는 열거형 클래스
 *
 * 주요 기능
 *  - 정렬 코드와 코드명을 통해 다양한 정렬 옵션을 제공
 * =====================================================
 */
@Getter
@AllArgsConstructor
public enum FriendSort {
    APPROVEDAT("approvedAt"),
    REQUESTEDAT("effectiveRequestedAt");

    private final String fieldName;

    public static FriendSort from(String key) {
        return Arrays.stream(values())
                .filter(v -> v.name().equalsIgnoreCase(key))
                .findFirst()
                .orElseThrow(() ->
                        new InvalidRequestException()
                );
    }
}
