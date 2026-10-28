package com.apr.aprbackendassignment.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import org.hibernate.annotations.Comment;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
/** 엔티티 생성·수정 시각을 공통으로 기록한다. */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class CommTimeEntity {

    @CreatedDate
    @Comment("생성일")
    @Column(updatable = false)
    protected LocalDateTime createdTime;

    @LastModifiedDate
    @Comment("수정일")
    protected LocalDateTime updatedTime;
    @PrePersist
    protected void onCreate() {
        if(this.createdTime == null) {
            this.createdTime = LocalDateTime.now();
            this.updatedTime = LocalDateTime.now();
        }
    }
}
