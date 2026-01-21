package com.apr.aprbackendassignment.service.impl;

import com.apr.aprbackendassignment.common.response.PageResponse;
import com.apr.aprbackendassignment.repository.FriendRepository;
import com.apr.aprbackendassignment.service.FriendService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
/**
 * =====================================================
 * Class Name   : FriendServiceImpl
 * Description  :
 *  - 친구 관계 관리를 위한 서비스 구현 클래스
 *
 * 주요 기능
 *  - 친구 관계 비즈니스 로직 구현
 *  - 현재 예시에서는 고정된 사용자 ID를 사용하여 친구 목록을 조회
 * =====================================================
 */
@Service
@RequiredArgsConstructor
public class FriendServiceImpl implements FriendService {
    private static final Long CURRENT_USER_ID = 1L;

    private FriendRepository friendRepository;
    @Override
    public PageResponse<Object> getFriendsList(Pageable pageable) {
        return friendRepository.getFriendsList(CURRENT_USER_ID, pageable);
    }
}