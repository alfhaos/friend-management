package com.apr.aprbackendassignment.friendship.presentation;

import com.apr.aprbackendassignment.user.domain.Users;
import com.apr.aprbackendassignment.friendship.domain.FRIENDSHIP_STATUS;
import com.apr.aprbackendassignment.friendship.domain.Friendship;
import com.apr.aprbackendassignment.support.IntegrationTestSupport;
import com.apr.aprbackendassignment.ratelimit.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static com.apr.aprbackendassignment.friendship.domain.FRIENDSHIP_STATUS.REQUESTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

class FriendRequestApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RateLimitProperties limits;

    @Test
    void limitsRequestsPerUserWithoutAffectingAnotherUser() throws Exception {
        Users requester = fixture.user("requester");
        Users another = fixture.user("another");
        for (int i = 0; i < limits.getMaxRequests(); i++) {
            Users target = fixture.user("target-" + i);
            request(requester, target, false);
        }
        Users blockedTarget = fixture.user("blocked-target");
        request(requester, blockedTarget, true);
        assertThat(friendships.findByRequesterIdAndReceiverId(requester.getId(), blockedTarget.getId())).isNull();
        request(another, blockedTarget, false);
        assertThat(friendships.countUsersFriends(requester.getId(), REQUESTED)).isEqualTo(limits.getMaxRequests());
        assertThat(friendships.findByRequesterIdAndReceiverId(another.getId(), blockedTarget.getId()).getStatus())
                .isEqualTo(REQUESTED);
        assertThat(friendships.count()).isEqualTo(limits.getMaxRequests() + 1);
    }

    @Test
    void returnsFreshRateLimitBudgetForEachTestFixture() throws Exception {
        Users requester = fixture.user("requester");
        Users target = fixture.user("target");
        request(requester, target, false);
        assertThat(friendships.count()).isEqualTo(1);
    }

    @Test
    void exposesFriendRequestPathInOpenApiDocument() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/friends/request']").exists())
                .andExpect(jsonPath("$.paths['/api/friends'].get.responses.200.content.application/json.schema.$ref")
                        .value(containsString("FriendsResponse")))
                .andExpect(jsonPath("$.paths['/api/friends/requests'].get.responses.200.content.application/json.schema.$ref")
                        .value(containsString("FriendsRequestsResponse")));
    }

    @Test
    void returnsFriendListWithOnlyDocumentedSnakeCaseItemKeys() throws Exception {
        Users current = fixture.user("current");
        Users friend = fixture.user("friend");
        Friendship relationship = fixture.relationship(current, friend, FRIENDSHIP_STATUS.ACCEPTED);

        mockMvc.perform(get("/api/friends")
                        .param("page", "0")
                        .param("maxSize", "10")
                        .param("sort", "approvedAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].user_id").value(friend.getId()))
                .andExpect(jsonPath("$.data.items[0].from_user_id").value(current.getId()))
                .andExpect(jsonPath("$.data.items[0].to_user_id").value(friend.getId()))
                .andExpect(jsonPath("$.data.items[0].approvedAt").exists())
                .andExpect(jsonPath("$.data.items[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].fromUserId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].toUserId").doesNotExist());

        assertThat(relationship.getStatus()).isEqualTo(FRIENDSHIP_STATUS.ACCEPTED);
    }

    @Test
    void returnsRequestWindowResponseWithoutLegacyPageFieldAndSnakeCaseItemKeys() throws Exception {
        Users receiver = fixture.user("receiver");
        Users requester = fixture.user("requester");
        Friendship pending = fixture.relationship(requester, receiver, FRIENDSHIP_STATUS.REQUESTED);

        mockMvc.perform(get("/api/friends/requests")
                        .param("page", "0")
                        .param("maxSize", "10")
                        .param("sort", "requestedAt,desc")
                        .param("window", "1D"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.window").value("1d"))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].request_id").value(pending.getId().toString()))
                .andExpect(jsonPath("$.data.items[0].request_user_id").value(requester.getId()))
                .andExpect(jsonPath("$.data.items[0].requestedAt").exists())
                .andExpect(jsonPath("$.data.totalPages").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].requestId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].requestUserId").doesNotExist());
    }

    private void request(Users requester, Users target, boolean blocked) throws Exception {
        mockMvc.perform(post("/api/friends/request")
                        .header("X-USER-ID", requester.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FriendRequest(target.getId()))))
                .andExpect(blocked ? status().isTooManyRequests() : status().isOk())
                .andExpect(jsonPath("$.code").value(blocked ? "TOO_MANY_REQUEST" : "SUCCESS"));
    }
}
