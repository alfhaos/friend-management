package com.apr.aprbackendassignment.model.dto;

import com.apr.aprbackendassignment.model.entity.Users;
import lombok.Builder;
import lombok.Getter;
/**
 * =====================================================
 * Class Name   : UserDto
 * Description  :
 *  - 사용자 데이터를 계층간 이동을 위한 DTO 클래스
 *
 * 주요 기능
 *  - 엔티티-DTO 변환: fromEntity 메서드를 통해 User 엔티티를 UserDto로 변환
 * =====================================================
 */
@Getter
@Builder
public class UserDto {

    private Long id;

    // 엔티티를 DTO로 변환하는 메서드
    public UserDto fromEntity(Users user) {
        return UserDto.builder()
                .id(user.getId())
                .build();
    }
}
