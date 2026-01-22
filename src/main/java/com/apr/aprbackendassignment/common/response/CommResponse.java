package com.apr.aprbackendassignment.common.response;
/**
 * =====================================================
 * Class Name   : CommResponse
 * Description  :
 *  - 공통 응답을 처리 하는 record 클래스
 *  - 제네릭 타입 T를 사용하여 다양한 데이터 타입을 응답에 포함할 수 있도록 설계
 *  - 응답 코드, 메시지, 데이터 필드를 포함
 *
 * 주요 기능
 *  - 성공 응답 생성: success(T data) 메서드를 통해 성공 응답을 생성
 *  - 실패 응답 생성: error(CommResponseStatus status) 메서드를 통해 실패 응답을 생성
 * =====================================================
 */
public record CommResponse<T>(
        String code,
        String message,
        Integer status,
        T data) {
    // 성공
    public static <T> CommResponse<T> success(T data) {
        return new CommResponse<>(
                null,
                null,
                null,
                data
        );
    }
    // 성공 (데이터 없음)
    public static CommResponse<CommResponseStatus> success() {
        return new CommResponse<>(
                CommResponseStatus.SUCCESS.getCode(),
                CommResponseStatus.SUCCESS.getMessage(),
                CommResponseStatus.SUCCESS.getHttpStatus().value(),
                null
        );
    }

    // 실패
    public static CommResponse<?> error(CommResponseStatus status) {
        return new CommResponse<>(
                status.getCode(),
                status.getMessage(),
                status.getHttpStatus().value(),
                null
        );
    }
}
