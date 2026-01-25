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

    // 4xx Client Error
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "잘못된 요청입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not Found"),
    NOT_FOUND_USER(HttpStatus.NOT_FOUND, "NOT_FOUND_USER", "해당 사용자를 찾을수 없습니다."),
    SELF_FRIEND_REQUEST(HttpStatus.BAD_REQUEST, "SELF_FRIEND_REQUEST", "자기 자신에게는 친구 요청을 보낼 수 없습니다."),
    ALREADY_REQUESTED_FRIEND(HttpStatus.CONFLICT, "ALREADY_REQUESTED_FRIEND", "이미 친구 요청이 존재합니다."),
    TOO_MANY_REQUEST(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUEST", "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    NOT_FOUND_FRIENDSHIP(HttpStatus.NOT_FOUND, "NOT_FOUND_FRIENDSHIP", "해당 친구관계를 찾을수 없습니다."),
    REQUEST_STATUS_ERROR(HttpStatus.BAD_REQUEST, "REQUEST_STATUS_ERROR", "해당 요청은 처리할수 없습니다."),
    FRIEND_LIMIT_EXCEEDED_FOR_ACCEPTOR(HttpStatus.BAD_REQUEST, "FRIEND_LIMIT_EXCEEDED_FOR_ACCEPTOR", "친구 수가 최대 한도를 초과하여 요청을 수락 할 수 없습니다."),
    FRIEND_LIMIT_EXCEEDED_REQUESTER(HttpStatus.BAD_REQUEST, "FRIEND_LIMIT_EXCEEDED_REQUESTER", "상대방의 친구 수가 최대 한도를 초과하여 요청을 수락 할 수 없습니다."),
    ALREADY_RREQUESTED_FRIENDSHIP(HttpStatus.BAD_REQUEST, "ALREADY_RREQUESTED_FRIENDSHIP", "이미 요청이 존재합니다."),
    ONLY_RECEIVER_CAN_PROCESS(HttpStatus.FORBIDDEN, "ONLY_RECEIVER_CAN_PROCESS", "해당 요청은 수신자만 처리할 수 있습니다."),
    CONCURRENT_FRIEND_REQUEST(HttpStatus.LOCKED, "CONCURRENT_FRIEND_REQUEST", "이미 처리 중인 친구 요청이 있습니다. 잠시 후 다시 시도해주세요."),

    // 5xx Server Error
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "처리 중 서버에서 에러가 발생했습니다."),
    LOCK_INTERRUPTED(HttpStatus.INTERNAL_SERVER_ERROR, "LOCK_INTERRUPTED", "요청 처리 중 인터럽트가 발생했습니다. 다시 시도해주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
