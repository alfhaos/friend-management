package com.apr.aprbackendassignment.common.response;

import com.apr.aprbackendassignment.model.constant.WINDOW_SLIDING;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class WindowResponse<T> {
    private WINDOW_SLIDING window;
    private int totalCount;
    private List<T> items;
}