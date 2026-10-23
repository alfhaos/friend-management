package com.apr.aprbackendassignment.controller;

import com.apr.aprbackendassignment.model.dto.request.FriendRequest;
import com.apr.aprbackendassignment.model.entity.Users;
import com.apr.aprbackendassignment.repository.FriendshipJPARepository;
import com.apr.aprbackendassignment.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FriendConstraintCollisionApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoSpyBean
    private FriendshipJPARepository spyFriendships;

    @Test
    void realUniqueConstraintReturnsConflictAndRollsBackBothWrites() throws Exception {
        Users requester = fixture.user("requester");
        Users receiver = fixture.user("receiver");
        doAnswer(invocation -> {
            // Create a stale-read collision in the same service transaction.
            jdbc.update("""
                    INSERT INTO friendship (id, requester_id, receiver_id, status, created_time, updated_time)
                    VALUES (?, ?, ?, 'REQUESTED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, UUID.randomUUID(), receiver.getId(), requester.getId());
            return List.of();
        }).when(spyFriendships).findBetweenUsers(eq(requester.getId()), eq(receiver.getId()));

        mockMvc.perform(post("/api/friends/request")
                        .header("X-USER-ID", requester.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FriendRequest(receiver.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("FRIEND_REQUEST_CONFLICT"));
        assertThat(friendships.count()).isZero();
    }
}
