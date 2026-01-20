package com.apr.aprbackendassignment.model.entity;

import com.apr.aprbackendassignment.model.constant.FRIENDSHIP_STATUS;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

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
@Entity
@Table(name = "FRIENDSHIP",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"requester_id", "receiver_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Friendship {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    @JoinColumn(name = "requester_id")
    private Users requester;

    @ManyToOne
    @JoinColumn(name = "receiver_id")
    private Users receiver;

    @Enumerated(EnumType.STRING)
    private FRIENDSHIP_STATUS status;
}




