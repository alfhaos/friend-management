package com.apr.aprbackendassignment.model.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * =====================================================
 * Class Name   : FRIENDSHIP_STATUS
 * Description  :
 *  - 친구 관계 상태를 정의하는 열거형 클래스
 *
 * 주요 기능
 *  - 상태 코드와 코드명을 통해 다양한 친구 관계 상태를 제공
 * =====================================================
 */
@Getter
@AllArgsConstructor
public enum FRIENDSHIP_STATUS {

    REQUESTED,
    ACCEPTED,
    REJECTED,
    BLOCKED;
}
