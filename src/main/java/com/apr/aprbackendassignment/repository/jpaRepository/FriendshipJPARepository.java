package com.apr.aprbackendassignment.repository.jpaRepository;

import com.apr.aprbackendassignment.model.entity.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
/**
 * =====================================================
 * Class Name   : FriendshipJPARepository
 * Description  :
 *  - 친구 관계 관리를 위한 JPA 리포지토리 인터페이스
 *
 * 주요 기능
 *  - 기본 CRUD 기능 제공을 위한 JpaRepository 상속
 * =====================================================
 */
public interface FriendshipJPARepository extends JpaRepository<Friendship, Long> {
}
