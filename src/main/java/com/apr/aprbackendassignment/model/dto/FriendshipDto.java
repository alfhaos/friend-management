package com.apr.aprbackendassignment.model.dto;

import com.apr.aprbackendassignment.model.constant.FRIENDSHIP_STATUS;
import com.apr.aprbackendassignment.model.entity.Friendship;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;

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
    private LocalDateTime approvedAt;


    // 친구 목록 조회에서 friendshipPage 를 FriendshipDto Page 로 변환
    public static Page<FriendshipDto> fromEntityPage(Page<Friendship> friendshipPage, Long CURRENT_USER_ID) {
        return friendshipPage.map(f -> {
            Long friendId = f.getRequester().getId().equals(CURRENT_USER_ID)
                    ? f.getReceiver().getId() :  f.getRequester().getId();
            return FriendshipDto.builder()
                    .id(friendId)
                    .requester(f.getRequester().getId())
                    .receiver(f.getReceiver().getId())
                    .approvedAt(f.getCreatedTime())
                    .build();
        });
    }
}
