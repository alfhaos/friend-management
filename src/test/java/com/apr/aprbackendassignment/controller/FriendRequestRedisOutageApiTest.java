package com.apr.aprbackendassignment.controller;

import com.apr.aprbackendassignment.model.dto.request.FriendRequest;
import com.apr.aprbackendassignment.model.entity.Users;
import com.apr.aprbackendassignment.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "limit.max-requests=2",
        "limit.window-milli-seconds=60000"
})
class FriendRequestRedisOutageApiTest extends IntegrationTestSupport {

    @Test
    void listingIncomingRequestsDoesNotConsumeApplicationRateLimit() throws Exception {
        Users viewer = fixture.user("viewer");
        doThrow(new RedisException("simulated Redis rate limiter failure"))
                .when(redisClient).getRateLimiter(anyString());

        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(get("/api/friends/requests")
                            .header("X-USER-ID", viewer.getId())
                            .param("window", "OVER"))
                    .andExpect(status().isOk());
        }
        verify(redisClient, times(0)).getRateLimiter(anyString());
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoSpyBean
    private RedissonClient redisClient;

    @Test
    void redisRateLimiterFailureUsesFinitePerUserLimitAcrossHttpRequests() throws Exception {
        Users requester = fixture.user("requester");
        Users other = fixture.user("other");
        Users first = fixture.user("first");
        Users second = fixture.user("second");
        Users blocked = fixture.user("blocked");
        Users otherTarget = fixture.user("other-target");
        doThrow(new RedisException("simulated Redis rate limiter failure"))
                .when(redisClient).getRateLimiter(anyString());

        request(requester, first, false);
        request(requester, second, false);
        verify(redisClient, times(1)).getRateLimiter(anyString());
        request(requester, blocked, true);
        request(other, otherTarget, false);

        assertThat(friendships.count()).isEqualTo(3);
        assertThat(friendships.findByRequesterIdAndReceiverId(requester.getId(), blocked.getId())).isNull();
        assertThat(friendships.findByRequesterIdAndReceiverId(other.getId(), otherTarget.getId())).isNotNull();
    }

    private void request(Users requester, Users receiver, boolean blocked) throws Exception {
        mockMvc.perform(post("/api/friends/request")
                        .header("X-USER-ID", requester.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FriendRequest(receiver.getId()))))
                .andExpect(blocked ? status().isTooManyRequests() : status().isOk())
                .andExpect(jsonPath("$.code").value(blocked ? "TOO_MANY_REQUEST" : "SUCCESS"));
    }
}
