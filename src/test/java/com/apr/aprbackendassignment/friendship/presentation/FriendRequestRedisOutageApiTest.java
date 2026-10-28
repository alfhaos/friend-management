package com.apr.aprbackendassignment.friendship.presentation;

import com.apr.aprbackendassignment.user.domain.Users;
import com.apr.aprbackendassignment.support.IntegrationTestSupport;
import com.apr.aprbackendassignment.ratelimit.RedisRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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

    @Autowired
    private RedisRateLimiter limiter;

    @BeforeEach
    void resetRateLimiterState() {
        ((Map<?, ?>) ReflectionTestUtils.getField(limiter, "localWindows")).clear();
        ((AtomicLong) ReflectionTestUtils.getField(limiter, "redisRetryAfterNanos")).set(0L);
        ((AtomicBoolean) ReflectionTestUtils.getField(limiter, "redisProbeInProgress")).set(false);
    }

    @Test
    void listingIncomingRequestsDoesNotConsumeApplicationRateLimit() throws Exception {
        Users viewer = fixture.user("viewer");
        doThrow(new RedisException("simulated Redis rate limiter failure"))
                .when(redisClient).getRateLimiter(anyString());

        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(get("/api/friends/requests")
                            .header("X-USER-ID", viewer.getId())
                            .param("window", "OVER")
                            .param("sort", "REQUESTEDAT,DESC"))
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

    @Test
    void semicolonPathAliasStillConsumesApplicationRateLimit() throws Exception {
        Users requester = fixture.user("alias-requester");
        Users first = fixture.user("alias-first");
        Users second = fixture.user("alias-second");
        Users blocked = fixture.user("alias-blocked");
        doThrow(new RedisException("simulated Redis rate limiter failure"))
                .when(redisClient).getRateLimiter(anyString());

        requestWithPath("/api/friends/request;x=1", requester, first, false);
        requestWithPath("/api/friends/request;x=1", requester, second, false);
        requestWithPath("/api/friends/request;x=1", requester, blocked, true);

        assertThat(friendships.findByRequesterIdAndReceiverId(requester.getId(), blocked.getId())).isNull();
    }

    private void request(Users requester, Users receiver, boolean blocked) throws Exception {
        requestWithPath("/api/friends/request", requester, receiver, blocked);
    }

    private void requestWithPath(String path, Users requester, Users receiver, boolean blocked) throws Exception {
        mockMvc.perform(post(path)
                        .header("X-USER-ID", requester.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FriendRequest(receiver.getId()))))
                .andExpect(blocked ? status().isTooManyRequests() : status().isOk())
                .andExpect(jsonPath("$.code").value(blocked ? "TOO_MANY_REQUEST" : "SUCCESS"));
    }
}
