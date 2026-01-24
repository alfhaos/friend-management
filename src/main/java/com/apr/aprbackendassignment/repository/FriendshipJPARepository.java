package com.apr.aprbackendassignment.repository;

import com.apr.aprbackendassignment.model.constant.FRIENDSHIP_STATUS;
import com.apr.aprbackendassignment.model.entity.Friendship;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
public interface FriendshipJPARepository extends JpaRepository<Friendship, UUID> {
    @Query("""
    SELECT f
    FROM Friendship f
    WHERE (f.requester.id = :userId OR f.receiver.id = :userId)
    AND f.status = :status
    """)
    Page<Friendship> getFriendsList(
            @Param("userId") Long userId,
            @Param("status") FRIENDSHIP_STATUS status,
            Pageable pageable
    );
    @Query("""
    SELECT f
    FROM Friendship f
    WHERE f.receiver.id = :userId
      AND (
           :start IS NULL 
           OR (f.createdTime >= :start AND f.createdTime < :end)
      )
      AND f.status = 'REQUESTED'
    """)
    Page<Friendship> getFriendsReceiveList(
            @Param("userId") Long currentUserId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
            );
    @Query("""
    SELECT f
    FROM Friendship f
    WHERE f.requester.id = :xUserId
      AND f.receiver.id = :targetUserId
    """)
    Friendship findFriendRequest(
            @Param("xUserId") Long xUserId
            ,@Param("targetUserId") Long targetUserId);
    @Query("""
    SELECT COUNT(f)
    FROM Friendship f
    WHERE (f.requester.id = :userId OR f.receiver.id = :userId)
    AND f.status = :status
    """)
    int countUsersFriends(@Param("userId") Long userId
            ,@Param("status") FRIENDSHIP_STATUS status);
    @Query("""
    SELECT f
    FROM Friendship f
    WHERE ((f.requester.id = :requesterId AND f.receiver.id = :targetUserId) 
       OR (f.requester.id = :targetUserId AND f.receiver.id = :requesterId))
    """)
    Friendship checkExistingRequest(
            @Param("requesterId") Long requesterId
            ,@Param("targetUserId") Long targetUserId);

    @Query("""
    SELECT f
    FROM Friendship f
    WHERE f.receiver.id = :userId
      AND f.status = :status
    """)
    List<Friendship> findByAcceptorAndStatus(
            @Param("userId") Long userId
            ,@Param("status") FRIENDSHIP_STATUS status);
}
