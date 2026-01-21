package com.apr.aprbackendassignment.repository.jpaRepository;

import com.apr.aprbackendassignment.common.exception.CommException;
import com.apr.aprbackendassignment.common.response.CommResponseStatus;
import com.apr.aprbackendassignment.model.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
/**
 * =====================================================
 * Class Name   : UsersJPARepository
 * Description  :
 *  - 사용자 정보 관리를 위한 JPA 리포지토리 인터페이스
 *
 * 주요 기능
 *  - 기본 CRUD 기능 제공을 위한 JpaRepository 상속
 * =====================================================
 */
public interface UsersJPARepository extends JpaRepository<Users, Long> {
    default Users findByIdOrThrow(Long currentUserId) {
        return  findById(currentUserId).orElseThrow(() -> new CommException(CommResponseStatus.NOT_FOUND_USER));
    };
}
