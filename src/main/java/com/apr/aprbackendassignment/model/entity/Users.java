package com.apr.aprbackendassignment.model.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
/**
 * =====================================================
 * Class Name   : User
 * Description  :
 *  - 사용자 정보를 관리하는 엔티티 클래스
 *
 * 주요 기능
 *  - 사용자 ID를 고유 식별자로 관리
 * =====================================================
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "Users")
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "user_name")
    private String name;

    public Users(String name) {
        this.name = name;
    }
    public static Users create (String name) {
       return new Users(name);
    }
}
