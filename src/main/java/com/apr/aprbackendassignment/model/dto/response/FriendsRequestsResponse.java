package com.apr.aprbackendassignment.model.dto.response;

import com.apr.aprbackendassignment.model.entity.Friendship;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
/**
 * =====================================================
 * Class Name   : FriendsRequestsResponse
 * Description  :
 *  - 사용자 데이터를 계층간 이동을 위한 DTO 클래스
 *
 * 주요 기능
 *  - 엔티티-DTO 변환: fromEntityPage 메서드를 통해 Friendship 엔티티를 FriendsRequestsResponse 변환
 * =====================================================
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendsRequestsResponse {

    private String requestId;
    private Long requestUserId;
    private LocalDateTime requestedAt;

    // 친구 목록 조회에서 friendshipPage 를 FriendshipDto Page 로 변환
    public static Page<FriendsRequestsResponse> fromEntityPage(Page<Friendship> friendshipPage) {
        return friendshipPage.map(f -> {
            return FriendsRequestsResponse.builder()
                    .requestId(String.valueOf(f.getId()))
                    .requestUserId(f.getRequester().getId())
                    .requestedAt(f.getCreatedTime())
                    .build();
        });
    }
}
