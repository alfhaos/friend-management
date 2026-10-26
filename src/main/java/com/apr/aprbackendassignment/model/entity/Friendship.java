package com.apr.aprbackendassignment.model.entity;

import com.apr.aprbackendassignment.common.entity.CommTimeEntity;
import com.apr.aprbackendassignment.model.constant.FRIENDSHIP_STATUS;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Formula;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * =====================================================
 * Class Name   : Friendship
 * Description  :
 *  - 친구 관계 상태를 정의하는 엔티티 클래스
 *
 * 주요 기능
 *  - 친구 요청자와 수신자 간의 관계를 관리
 * =====================================================
 */
@Getter
@Entity
@Table(name = "FRIENDSHIP",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"requester_id", "receiver_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Friendship extends CommTimeEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private Users requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private Users receiver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FRIENDSHIP_STATUS status;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Formula("coalesce(requested_at, created_time)")
    private LocalDateTime effectiveRequestedAt;

    private Friendship(Users requester, Users receiver, LocalDateTime requestedAt) {
        this.requester = Objects.requireNonNull(requester, "requester must not be null");
        this.receiver = Objects.requireNonNull(receiver, "receiver must not be null");
        if (requester == receiver
                || requester.getId() != null && requester.getId().equals(receiver.getId())) {
            throw new IllegalArgumentException("requester and receiver must be different users");
        }
        this.status = FRIENDSHIP_STATUS.REQUESTED;
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt must not be null");
    }

    public static Friendship request(Users requester, Users receiver, LocalDateTime now) {
        return new Friendship(requester, receiver, now);
    }

    public void requestAgain(Long actorId, LocalDateTime now) {
        requireActor(actorId, requester);
        requireStatus(FRIENDSHIP_STATUS.REJECTED);
        LocalDateTime requestedAt = Objects.requireNonNull(now, "requestedAt must not be null");
        status = FRIENDSHIP_STATUS.REQUESTED;
        this.requestedAt = requestedAt;
        approvedAt = null;
    }

    public void accept(Long actorId, LocalDateTime now) {
        requireActor(actorId, receiver);
        requireStatus(FRIENDSHIP_STATUS.REQUESTED);
        LocalDateTime approvedAt = Objects.requireNonNull(now, "approvedAt must not be null");
        status = FRIENDSHIP_STATUS.ACCEPTED;
        this.approvedAt = approvedAt;
    }

    public void reject(Long actorId) {
        requireActor(actorId, receiver);
        requireStatus(FRIENDSHIP_STATUS.REQUESTED);
        status = FRIENDSHIP_STATUS.REJECTED;
    }

    private void requireActor(Long actorId, Users requiredActor) {
        if (actorId == null || !actorId.equals(requiredActor.getId())) {
            throw new IllegalArgumentException("actor is not authorized for this transition");
        }
    }

    private void requireStatus(FRIENDSHIP_STATUS requiredStatus) {
        if (status != requiredStatus) {
            throw new IllegalStateException("friendship status does not allow this transition");
        }
    }
}




