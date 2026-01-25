package com.apr.aprbackendassignment.common.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
/**
 * =====================================================
 * Class Name   : PageResponse
 * Description  :
 *  - 페이지네이션 응답을 처리 하는 클래스

 * 주요 기능
 *  - 총 페이지 수(totalPages), 총 항목 수(totalCount), 항목 목록(items) 필드를 포함
 * =====================================================
 */
@Getter
@AllArgsConstructor
public class PageResponse<T> {
    private int totalPages;
    private int totalCount;
    private List<T> items;
}