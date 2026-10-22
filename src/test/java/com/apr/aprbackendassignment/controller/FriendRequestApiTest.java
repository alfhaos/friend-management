package com.apr.aprbackendassignment.controller;

import com.apr.aprbackendassignment.model.dto.request.FriendRequest;
import com.apr.aprbackendassignment.model.entity.Users;
import com.apr.aprbackendassignment.support.IntegrationTestSupport;
import com.apr.aprbackendassignment.util.LimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static com.apr.aprbackendassignment.model.constant.FRIENDSHIP_STATUS.REQUESTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FriendRequestApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private LimitProperties limits;

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

    private void request(Users requester, Users target, boolean blocked) throws Exception {
        mockMvc.perform(post("/api/friends/request")
                        .header("X-USER-ID", requester.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FriendRequest(target.getId()))))
                .andExpect(blocked ? status().isTooManyRequests() : status().isOk())
                .andExpect(jsonPath("$.code").value(blocked ? "TOO_MANY_REQUEST" : "SUCCESS"));
    }
}
