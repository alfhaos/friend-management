package com.apr.aprbackendassignment.model.entity;

import com.apr.aprbackendassignment.common.entity.CommTimeEntity;
import com.apr.aprbackendassignment.model.constant.FRIENDSHIP_STATUS;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Formula;

import java.time.LocalDateTime;
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

    @Formula("coalesce(requested_at, created_time)")
    private LocalDateTime effectiveRequestedAt;

    public Friendship(Users requester, Users receiver, FRIENDSHIP_STATUS status) {
        this.requester = requester;
        this.receiver = receiver;
        this.status = status;
        this.requestedAt = LocalDateTime.now();
    }

    public static Friendship create(Users requester, Users receiver, FRIENDSHIP_STATUS status) {
        return new Friendship(requester, receiver, status);
    }

    public void updateUpdateAt(LocalDateTime time) {
        this.updatedTime = time;
    }
    public void updateRequestedAt(LocalDateTime time) {
        this.requestedAt = time;
    }
    public void updateStatus(FRIENDSHIP_STATUS status) {
        this.status = status;
    }

    public void updateRequester(Users requester) {
        this.requester = requester;
    }
    public void updateReceiver(Users receiver) {
        this.receiver = receiver;
    }
}




