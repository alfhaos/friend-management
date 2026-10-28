package com.apr.aprbackendassignment.friendship.application;

import com.apr.aprbackendassignment.common.exception.InvalidRequestException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * =====================================================
 * Class Name   : RequestWindow
 * Description  :
 *  - 윈도우 슬라이딩 기간을 정의하는 열거형 클래스
 *
 * 주요 기능
 *  - 기간 코드와 코드명을 통해 다양한 기간 옵션을 제공
 * =====================================================
 */
@Getter
@AllArgsConstructor
public enum RequestWindow {

    DAY("1d", 1),
    WEEK("7d", 7),
    MONTH("30d", 30),
    NINETY_DAY("90d", 90),
    OVER("over", 0);

    private final String code;
    private final int codeDate;

    // request의 code를 enum으로 변환
    public static RequestWindow from(String code) {
        return Arrays.stream(values())
                .filter(v -> v.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() ->
                        new InvalidRequestException());
    }

    public LocalDateTime calculateTimeWindow(LocalDateTime now) {
        if (this == OVER) {
            return null; // 전체 조회용
        }
        return now.minusDays(codeDate);
    }
}
