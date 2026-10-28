package com.apr.aprbackendassignment.friendship.presentation;

import com.apr.aprbackendassignment.friendship.domain.FRIENDSHIP_STATUS;
import com.apr.aprbackendassignment.friendship.domain.Friendship;
import com.apr.aprbackendassignment.user.domain.Users;
import com.apr.aprbackendassignment.support.IntegrationTestSupport;
import com.apr.aprbackendassignment.ratelimit.RateLimitProperties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FriendInputAndBusinessApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RateLimitProperties limits;

    @Test
    void invalidHeadersAndRequestInputsReturnBadRequestWithoutWriting() throws Exception {
        Users user = fixture.user("observer");
        expectBadRequest(post("/api/friends/accept/not-a-uuid"));
        expectBadRequest(post("/api/friends/request").header("X-user-Id", 0)
                .contentType(MediaType.APPLICATION_JSON).content("{\"targetUserId\":" + user.getId() + "}"));
        expectBadRequest(post("/api/friends/accept/not-a-uuid").header("X-user-Id", "abc"));
        expectBadRequest(post("/api/friends/request").header("X-user-Id", user.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
        expectBadRequest(post("/api/friends/request").header("X-user-Id", user.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"targetUserId\":0}"));
        expectBadRequest(get("/api/friends/requests").param("sort", "requestedAt,desc"));
        expectBadRequest(get("/api/friends/requests")
                .param("sort", "requestedAt,desc").param("window", "bad"));
        expectBadRequest(get("/api/friends/requests").param("window", "over"));
        expectBadRequest(get("/api/friends").param("sort", "approvedAt,desc").param("page", "-1"));
        expectBadRequest(get("/api/friends").param("sort", "approvedAt,desc").param("maxSize", "0"));
        expectBadRequest(get("/api/friends").param("sort", "approvedAt,desc,"));
        assertThat(friendships.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "999999999999999999999999"})
    void malformedRequestHeaderIsBadRequestBeforeRateLimitAndDoesNotWrite(String headerValue) throws Exception {
        Users receiver = fixture.user("receiver");

        expectBadRequest(post("/api/friends/request")
                .header("X-user-Id", headerValue)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetUserId\":" + receiver.getId() + "}"));

        assertThat(friendships.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void nonPositiveRequestHeaderIsBadRequestBeforeRateLimitAndDoesNotWrite(long headerValue) throws Exception {
        Users receiver = fixture.user("receiver");

        for (int attempt = 0; attempt <= limits.getMaxRequests(); attempt++) {
            expectBadRequest(post("/api/friends/request")
                    .header("X-user-Id", headerValue)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"targetUserId\":" + receiver.getId() + "}"));
        }

        assertThat(friendships.count()).isZero();
    }

    private void expectBadRequest(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"accept", "reject"})
    void malformedRequestIdReturnsBadRequestWithoutChangingRelationship(String action) throws Exception {
        Users requester = fixture.user("requester");
        Users receiver = fixture.user("receiver");
        Friendship pending = fixture.relationship(requester, receiver, FRIENDSHIP_STATUS.REQUESTED);
        Map<String, Object> before = snapshot(pending);

        mockMvc.perform(post("/api/friends/" + action + "/not-a-uuid")
                        .header("X-user-Id", receiver.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(snapshot(pending)).isEqualTo(before);
        assertThat(friendships.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"accept", "reject"})
    void nonReceiverCannotProcessRequestOrChangeRelationship(String action) throws Exception {
        Users requester = fixture.user("requester");
        Users receiver = fixture.user("receiver");
        Users outsider = fixture.user("outsider");
        Friendship pending = fixture.relationship(requester, receiver, FRIENDSHIP_STATUS.REQUESTED);
        Map<String, Object> before = snapshot(pending);

        mockMvc.perform(post("/api/friends/" + action + "/" + pending.getId())
                        .header("X-user-Id", outsider.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("ONLY_RECEIVER_CAN_PROCESS"))
                .andExpect(jsonPath("$.message").value("해당 요청은 수신자만 처리할 수 있습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(snapshot(pending)).isEqualTo(before);
        assertThat(friendships.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"accept", "reject"})
    void completedRequestCannotBeProcessedAgainOrChanged(String action) throws Exception {
        Users requester = fixture.user("requester");
        Users receiver = fixture.user("receiver");
        Friendship accepted = fixture.relationship(requester, receiver, FRIENDSHIP_STATUS.ACCEPTED);
        Map<String, Object> before = snapshot(accepted);

        mockMvc.perform(post("/api/friends/" + action + "/" + accepted.getId())
                        .header("X-user-Id", receiver.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("REQUEST_STATUS_ERROR"))
                .andExpect(jsonPath("$.message").value("해당 요청은 처리할수 없습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(snapshot(accepted)).isEqualTo(before);
        assertThat(friendships.count()).isEqualTo(1);
    }

    private Map<String, Object> snapshot(Friendship friendship) {
        return jdbc.queryForMap("SELECT requester_id, receiver_id, status, requested_at, approved_at, updated_time "
                + "FROM friendship WHERE id = ?", friendship.getId());
    }
}
