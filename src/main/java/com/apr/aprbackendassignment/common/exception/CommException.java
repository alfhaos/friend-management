package com.apr.aprbackendassignment.common.exception;

import com.apr.aprbackendassignment.common.response.CommResponseStatus;
import lombok.Getter;
/**
 * =====================================================
 * Class Name   : CommException
 * Description  :
 *  - 공통 예외를 처리 하는 클래스
 *
 * 주요 기능
 *  - 예외 발생 시 CommResponseStatus를 통해 상태 정보를 전달
 * =====================================================
 */
@Getter
public class CommException extends RuntimeException{
    private final CommResponseStatus status;
    public CommException(CommResponseStatus status) {
        super(status.getMessage());
        this.status = status;
    }
}
