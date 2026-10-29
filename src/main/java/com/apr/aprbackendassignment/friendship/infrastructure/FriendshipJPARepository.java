package com.apr.aprbackendassignment.friendship.infrastructure;

import com.apr.aprbackendassignment.friendship.domain.FRIENDSHIP_STATUS;
import com.apr.aprbackendassignment.friendship.domain.Friendship;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
      AND COALESCE(f.requestedAt, f.createdTime) >= :start
      AND COALESCE(f.requestedAt, f.createdTime) < :end
      AND f.status = 'REQUESTED'
    """)
    Page<Friendship> getFriendsReceiveListInWindow(
            @Param("userId") Long currentUserId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
            );

    @Query("""
    SELECT f
    FROM Friendship f
    WHERE f.receiver.id = :userId
      AND COALESCE(f.requestedAt, f.createdTime) < :before
      AND f.status = 'REQUESTED'
    """)
    Page<Friendship> getFriendsReceiveListBefore(
            @Param("userId") Long currentUserId,
            @Param("before") LocalDateTime before,
            Pageable pageable);

    Page<Friendship> findByReceiverIdAndStatus(Long id, FRIENDSHIP_STATUS status, Pageable pageable);

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
    WHERE (f.requester.id = :firstUserId AND f.receiver.id = :secondUserId)
       OR (f.requester.id = :secondUserId AND f.receiver.id = :firstUserId)
    """)
    List<Friendship> findBetweenUsers(@Param("firstUserId") Long firstUserId,
                                      @Param("secondUserId") Long secondUserId);

    @Query("""
    SELECT f.requester.id AS requesterId, f.receiver.id AS receiverId
    FROM Friendship f
    WHERE f.id = :requestId
    """)
    Optional<ParticipantIds> findParticipantIds(@Param("requestId") UUID requestId);

    Friendship findByRequesterIdAndReceiverId(Long requesterId, Long receiverId);

    interface ParticipantIds {
        Long getRequesterId();
        Long getReceiverId();
    }
}
