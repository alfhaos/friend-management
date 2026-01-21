package com.apr.aprbackendassignment.common.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
/**
 * =====================================================
 * Class Name   : CommResponseStatus
 * Description  :
 *  - 공통 응답 상태를 정의하는 열거형(enum) 클래스
 *  - 각 상태는 HTTP 상태 코드, 응답 코드, 메시지를 포함
 *
 * 주요 기능
 *  - 성공 상태 정의: SUCCESS 상태를 통해 성공 응답을 나타냄
 *  - 공통 오류 상태 정의: BAD_REQUEST, NOT_FOUND, INTERNAL_SERVER_ERROR 등의 상태를 통해 다양한 오류 상황을 나타냄
 * =====================================================
 */
@Getter
@AllArgsConstructor
public enum CommResponseStatus {

    // Success
    SUCCESS(HttpStatus.OK, "SUCCESS", "Success"),

    // Common
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Bad Request"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not Found"),
    NOT_FOUND_USER(HttpStatus.INTERNAL_SERVER_ERROR, "NOT_FOUND_USER", "Not Found User"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "Internal Server Error");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
