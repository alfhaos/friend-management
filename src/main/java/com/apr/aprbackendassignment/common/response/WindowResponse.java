package com.apr.aprbackendassignment.common.response;

import com.apr.aprbackendassignment.model.constant.WINDOW_SLIDING;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
/**
 * =====================================================
 * Class Name   : WindowResponse
 * Description  :
 *  - 슬라이딩 윈도우 응답을 처리 하는 클래스

 * 주요 기능
 *  - 윈도우 타입(window), 총 항목 수(totalCount), 항목 목록(items) 필드를 포함
 * =====================================================
 */
@Getter
@AllArgsConstructor
public class WindowResponse<T> {
    private WINDOW_SLIDING window;
    private int totalCount;
    private List<T> items;
}