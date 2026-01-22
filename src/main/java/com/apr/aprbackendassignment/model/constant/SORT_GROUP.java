package com.apr.aprbackendassignment.model.constant;

import com.apr.aprbackendassignment.common.exception.CommException;
import com.apr.aprbackendassignment.common.response.CommResponseStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * =====================================================
 * Class Name   : SORT_GROUP
 * Description  :
 *  - 정렬 그룹을 정의하는 열거형 클래스
 *
 * 주요 기능
 *  - 정렬 코드와 코드명을 통해 다양한 정렬 옵션을 제공
 * =====================================================
 */
@Getter
@AllArgsConstructor
public enum SORT_GROUP {
    APPROVEDAT("createdTime"),
    REQUESTEDAT("createdTime");

    private final String fieldName;

    public static SORT_GROUP from(String key) {
        return Arrays.stream(values())
                .filter(v -> v.name().equalsIgnoreCase(key))
                .findFirst()
                .orElseThrow(() ->
                        new CommException(CommResponseStatus.BAD_REQUEST)
                );
    }
}
