package com.apr.aprbackendassignment.model.dto;

import com.apr.aprbackendassignment.model.constant.FRIENDSHIP_STATUS;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
@NoArgsConstructor
@AllArgsConstructor
public class FriendshipDto {

    private Long id;
    private Long requester;
    private Long receiver;
    private FRIENDSHIP_STATUS status;
}
