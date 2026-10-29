package com.apr.aprbackendassignment.friendship.application;

import com.apr.aprbackendassignment.friendship.domain.Friendship;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
/**
 * =====================================================
 * Class Name   : FriendsResponse
 * Description  :
 *  - 사용자 데이터를 계층간 이동을 위한 DTO 클래스
 *
 * 주요 기능
 *  - 엔티티-DTO 변환: fromEntityPage 메서드를 통해 Friendship 엔티티를 FriendsResponse 변환
 * =====================================================
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendsResponse {

    @JsonProperty("user_id")
    private Long userId;
    @JsonProperty("from_user_id")
    private Long fromUserId;
    @JsonProperty("to_user_id")
    private Long toUserId;
    private LocalDateTime approvedAt;

    // 친구 목록 조회에서 friendshipPage 를 FriendshipDto Page 로 변환
    public static Page<FriendsResponse> fromEntityPage(Page<Friendship> friendshipPage, Long currentUserId) {
        return friendshipPage.map(f -> {
            Long friendId = f.getRequester().getId().equals(currentUserId)
                    ? f.getReceiver().getId() :  f.getRequester().getId();
            return FriendsResponse.builder()
                    .userId(friendId)
                    .fromUserId(f.getRequester().getId())
                    .toUserId(f.getReceiver().getId())
                    .approvedAt(f.getApprovedAt())
                    .build();
        });
    }
}
