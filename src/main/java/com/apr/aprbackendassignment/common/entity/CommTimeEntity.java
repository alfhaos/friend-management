package com.apr.aprbackendassignment.common.entity;

import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import org.hibernate.annotations.Comment;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
/**
 * =====================================================
 * Class Name   : CommTimeEntity
 * Description  :
 *  - 공통 시간 엔티티를 처리 하는 클래스
 *  - 생성자 ID(createUserId) 필드를 포함
 *
 * 주요 기능
 *  - 생성일 자동 설정: @CreatedDate 어노테이션을 사용하여 엔티티가 생성될 때 자동으로 생성일을 설정
 *  - 생성자 ID 필드 포함: createUserId 필드를 통해 엔티티를 생성한 사용자의 ID를 저장
 * =====================================================
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class CommTimeEntity {

    @CreatedDate
    @Comment("생성일")
    protected LocalDateTime createdTime;

    @PrePersist
    protected void onCreate() {

        if(this.createdTime == null) {

            this.createdTime = LocalDateTime.now();
        }
    }
}
