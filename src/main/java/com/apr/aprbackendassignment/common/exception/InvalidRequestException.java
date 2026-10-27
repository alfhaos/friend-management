package com.apr.aprbackendassignment.common.exception;

/**
 * API 경계에서 해석할 수 없는 입력을 나타낸다.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException() {
        super("Invalid request");
    }

    public InvalidRequestException(Throwable cause) {
        super("Invalid request", cause);
    }
}
