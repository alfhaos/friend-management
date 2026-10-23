package com.apr.aprbackendassignment.repository;

import com.apr.aprbackendassignment.model.entity.Users;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
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
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Users> findLockedById(Long id);
}
