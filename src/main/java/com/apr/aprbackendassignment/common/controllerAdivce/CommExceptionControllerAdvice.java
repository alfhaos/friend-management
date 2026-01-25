package com.apr.aprbackendassignment.common.controllerAdivce;

import com.apr.aprbackendassignment.common.exception.CommException;
import com.apr.aprbackendassignment.common.response.CommResponse;
import com.apr.aprbackendassignment.common.response.CommResponseStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * =====================================================
 * Class Name   : CommExceptionControllerAdvice
 * Description  :
 *  - 공통 예외를 처리 하는 Controller Advice 클래스
 *  - RestControllerAdvice 어노테이션을 사용하여 모든 컨트롤러에서 발생하는 예외를 처리
 *
 * 주요 기능
 *  - 커스텀 예외 처리: CommException 발생 시 적절한 HTTP 상태 코드와 응답 메시지를 반환
 * =====================================================
 */
@RestControllerAdvice
@Slf4j
public class CommExceptionControllerAdvice {
    // 커스텀 예외 처리
    @ExceptionHandler(CommException.class)
    public ResponseEntity<CommResponse<?>> handleBaseException(CommException e) {

        CommResponseStatus status = e.getStatus();

        return ResponseEntity
                .status(status.getHttpStatus())
                .body(CommResponse.error(status));
    }

    // 잘못된 파라미터
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<CommResponse<?>> handleParamException(Exception e) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(CommResponse.error(CommResponseStatus.BAD_REQUEST));
    }

    // 모든 예외 처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommResponse<?>> handleException(Exception e) {

        log.error("Internal Server Error: ", e);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CommResponse.error(CommResponseStatus.INTERNAL_SERVER_ERROR));
    }
}
