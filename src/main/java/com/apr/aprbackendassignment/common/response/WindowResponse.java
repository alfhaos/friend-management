package com.apr.aprbackendassignment.common.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class WindowResponse<T> {
    private String window;
    private int totalCount;
    private List<T> items;
}
