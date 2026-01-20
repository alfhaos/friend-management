package com.apr.aprbackendassignment.repository;

import com.apr.aprbackendassignment.repository.jpaRepository.FriendshipJPARepository;
import com.apr.aprbackendassignment.repository.jpaRepository.UsersJPARepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
/**
 * =====================================================
 * Class Name   : FriendRepository
 * Description  :
 *  - 친구 관계 관리를 위한 리포지토리 클래스
 *
 * 주요 기능
 *  - 친구 관계 데이터 접근 및 조작을 위한 메서드 제공
 * =====================================================
 */
@Repository
@RequiredArgsConstructor
public class FriendRepository {

    private final FriendshipJPARepository friendshipJPARepository;
    private final UsersJPARepository usersJPARepository;

}
