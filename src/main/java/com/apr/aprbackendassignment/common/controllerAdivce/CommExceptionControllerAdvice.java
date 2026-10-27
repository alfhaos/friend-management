package com.apr.aprbackendassignment.common.controllerAdivce;

import com.apr.aprbackendassignment.common.exception.CommException;
import com.apr.aprbackendassignment.common.exception.FriendshipError;
import com.apr.aprbackendassignment.common.exception.FriendshipException;
import com.apr.aprbackendassignment.common.exception.InvalidRequestException;
import com.apr.aprbackendassignment.common.response.CommResponse;
import com.apr.aprbackendassignment.common.response.CommResponseStatus;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLException;

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

    @ExceptionHandler(FriendshipException.class)
    public ResponseEntity<CommResponse<?>> handleFriendshipException(FriendshipException e) {
        return error(toResponseStatus(e.getError()));
    }

    // 잘못된 파라미터
    @ExceptionHandler({
            InvalidRequestException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            ServletRequestBindingException.class,
            BindException.class,
            jakarta.validation.ConstraintViolationException.class
    })
    public ResponseEntity<CommResponse<?>> handleParamException(Exception e) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(CommResponse.error(CommResponseStatus.BAD_REQUEST));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<CommResponse<?>> handleIntegrityException(DataIntegrityViolationException e) {
        if (isUniqueConflict(e)) {
            return error(CommResponseStatus.FRIEND_REQUEST_CONFLICT);
        }
        if (isDatabaseUnavailable(e)) {
            log.error("Database unavailable during integrity operation: ", e);
            return error(CommResponseStatus.DATABASE_UNAVAILABLE);
        }
        log.error("Database integrity failure: ", e);
        return error(CommResponseStatus.INTERNAL_SERVER_ERROR);
    }

    // 모든 예외 처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommResponse<?>> handleException(Exception e) {
        if (isDatabaseUnavailable(e)) {
            log.error("Database unavailable: ", e);
            return error(CommResponseStatus.DATABASE_UNAVAILABLE);
        }
        log.error("Internal Server Error: ", e);
        return error(CommResponseStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<CommResponse<?>> error(CommResponseStatus status) {
        return ResponseEntity.status(status.getHttpStatus()).body(CommResponse.error(status));
    }

    private CommResponseStatus toResponseStatus(FriendshipError error) {
        return switch (error) {
            case NOT_FOUND_USER -> CommResponseStatus.NOT_FOUND_USER;
            case SELF_FRIEND_REQUEST -> CommResponseStatus.SELF_FRIEND_REQUEST;
            case ALREADY_REQUESTED_FRIEND -> CommResponseStatus.ALREADY_REQUESTED_FRIEND;
            case NOT_FOUND_FRIENDSHIP -> CommResponseStatus.NOT_FOUND_FRIENDSHIP;
            case REQUEST_STATUS_ERROR -> CommResponseStatus.REQUEST_STATUS_ERROR;
            case FRIEND_LIMIT_EXCEEDED_FOR_ACCEPTOR -> CommResponseStatus.FRIEND_LIMIT_EXCEEDED_FOR_ACCEPTOR;
            case FRIEND_LIMIT_EXCEEDED_REQUESTER -> CommResponseStatus.FRIEND_LIMIT_EXCEEDED_REQUESTER;
            case ALREADY_RREQUESTED_FRIENDSHIP -> CommResponseStatus.ALREADY_RREQUESTED_FRIENDSHIP;
            case ONLY_RECEIVER_CAN_PROCESS -> CommResponseStatus.ONLY_RECEIVER_CAN_PROCESS;
            case CONCURRENT_FRIEND_REQUEST -> CommResponseStatus.CONCURRENT_FRIEND_REQUEST;
            case LOCK_INTERRUPTED -> CommResponseStatus.LOCK_INTERRUPTED;
        };
    }

    private boolean isUniqueConflict(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "23505".equals(violation.getSQLState())
                    && isFriendshipUniqueConstraint(violation.getConstraintName())) {
                return true;
            }
            if (cause == cause.getCause()) {
                break;
            }
        }
        return false;
    }

    private boolean isFriendshipUniqueConstraint(String constraintName) {
        return "ux_friendship_active_pair".equals(constraintName)
                || "uk8pnm9rovglqfddxo47d2afm35".equals(constraintName)
                || "friendship_requester_id_receiver_id_key".equals(constraintName);
    }

    private boolean isDatabaseUnavailable(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof DataAccessResourceFailureException
                    || cause instanceof PessimisticLockingFailureException
                    || cause instanceof QueryTimeoutException
                    || cause instanceof CannotCreateTransactionException) {
                return true;
            }
            if (cause instanceof SQLException sql) {
                String state = sql.getSQLState();
                if (state != null && (state.startsWith("08") || state.startsWith("53")
                        || state.equals("55P03") || state.equals("40P01"))) {
                    return true;
                }
            }
            if (cause == cause.getCause()) {
                break;
            }
        }
        return false;
    }
}
