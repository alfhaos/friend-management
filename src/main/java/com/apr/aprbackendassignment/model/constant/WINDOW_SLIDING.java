package com.apr.aprbackendassignment.model.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;
/**
 * =====================================================
 * Class Name   : WINDOW_SLIDING
 * Description  :
 *  - 윈도우 슬라이딩 기간을 정의하는 열거형 클래스
 *
 * 주요 기능
 *  - 기간 코드와 코드명을 통해 다양한 기간 옵션을 제공
 * =====================================================
 */
@Getter
@AllArgsConstructor
public enum WINDOW_SLIDING {

    DAY("1d", "DAY"),
    WEEK("7d", "WEEK"),
    MONTH("30d", "MONTH"),
    NINETY_DAY("90d", "NINETY_DAY"),
    OVER("over", "OVER");

    private final String code;
    private final String codeName;
}
