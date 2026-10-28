package com.apr.aprbackendassignment.friendship.presentation;

import com.apr.aprbackendassignment.common.exception.CommException;
import com.apr.aprbackendassignment.common.response.CommResponseStatus;
import com.apr.aprbackendassignment.user.domain.Users;
import com.apr.aprbackendassignment.support.IntegrationTestSupport;
import com.apr.aprbackendassignment.friendship.application.FriendRequestFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FriendFailureApiTest extends IntegrationTestSupport {

    @Test
    void unrelatedUniqueConstraintIsNotReportedAsFriendRequestConflict() throws Exception {
        Users requester = fixture.user("requester");
        Users receiver = fixture.user("receiver");
        SQLException cause = new SQLException(
                "duplicate key value violates unique constraint \"ux_other_table\"", "23505");
        doThrow(new DataIntegrityViolationException("unrelated unique conflict", cause))
                .when(facade).requestFriend(anyLong(), anyLong());

        mockMvc.perform(request(requester, receiver))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value(SERVER_ERROR_MESSAGE));
    }

    @Test
    void unrelatedHibernateUniqueConstraintIsNotReportedAsFriendRequestConflict() throws Exception {
        Users requester = fixture.user("requester");
        Users receiver = fixture.user("receiver");
        SQLException cause = new SQLException("duplicate key", "23505");
        doThrow(new DataIntegrityViolationException("unrelated unique conflict",
                new ConstraintViolationException("unrelated unique conflict", cause, "ux_other_table")))
                .when(facade).requestFriend(anyLong(), anyLong());

        mockMvc.perform(request(requester, receiver))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value(SERVER_ERROR_MESSAGE));
        assertThat(friendships.count()).isZero();
    }

    private static final String SERVER_ERROR_MESSAGE = "서버에 문제가 생겼습니다. 잠시 후 다시 시도해주세요.";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private FriendRequestFacade facade;

    @Test
    void returnsConflictForActivePairUniqueConstraintViolation() throws Exception {
        Users requester = fixture.user("requester");
        Users receiver = fixture.user("receiver");
        SQLException cause = new SQLException(
                "duplicate key value violates unique constraint \"ux_friendship_active_pair\"", "23505");
        doThrow(new DataIntegrityViolationException("active pair conflict",
                new ConstraintViolationException("active pair conflict", cause, "ux_friendship_active_pair")))
                .when(facade).requestFriend(anyLong(), anyLong());

        mockMvc.perform(request(requester, receiver))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("FRIEND_REQUEST_CONFLICT"))
                .andExpect(jsonPath("$.data").doesNotExist());
        assertThat(friendships.count()).isZero();
    }

    @Test
    void returnsConflictForDirectionalUniqueConstraintViolation() throws Exception {
        Users requester = fixture.user("requester");
        Users receiver = fixture.user("receiver");
        SQLException cause = new SQLException(
                "duplicate key value violates unique constraint \"uk_friendship_requester_receiver\"", "23505");
        doThrow(new DataIntegrityViolationException("directional pair conflict",
                new ConstraintViolationException("directional pair conflict", cause,
                        "friendship_requester_id_receiver_id_key")))
                .when(facade).requestFriend(anyLong(), anyLong());

        mockMvc.perform(request(requester, receiver))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("FRIEND_REQUEST_CONFLICT"))
                .andExpect(jsonPath("$.data").doesNotExist());
        assertThat(friendships.count()).isZero();
    }

    @Test
    void returnsConflictForDevelopmentDatabaseDirectionalConstraint() throws Exception {
        Users requester = fixture.user("requester");
        Users receiver = fixture.user("receiver");
        SQLException cause = new SQLException("duplicate key", "23505");
        doThrow(new DataIntegrityViolationException("directional pair conflict",
                new ConstraintViolationException("directional pair conflict", cause,
                        "uk8pnm9rovglqfddxo47d2afm35")))
                .when(facade).requestFriend(anyLong(), anyLong());

        mockMvc.perform(request(requester, receiver))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FRIEND_REQUEST_CONFLICT"));
        assertThat(friendships.count()).isZero();
    }

    @Test
    void returnsServiceUnavailableForDatabaseConnectionFailure() throws Exception {
        Users requester = fixture.user("requester");
        Users receiver = fixture.user("receiver");
        doThrow(new DataAccessResourceFailureException("database connection unavailable"))
                .when(facade).requestFriend(anyLong(), anyLong());

        mockMvc.perform(request(requester, receiver))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(SERVER_ERROR_MESSAGE))
                .andExpect(jsonPath("$.data").doesNotExist());
        assertThat(friendships.count()).isZero();
    }

    @Test
    void returnsServiceUnavailableForConnectionSqlStateInsideIntegrityException() throws Exception {
        Users requester = fixture.user("requester");
        Users receiver = fixture.user("receiver");
        SQLException cause = new SQLException("connection lost", "08006");
        doThrow(new DataIntegrityViolationException("database failed", cause))
                .when(facade).requestFriend(anyLong(), anyLong());

        mockMvc.perform(request(requester, receiver))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(SERVER_ERROR_MESSAGE))
                .andExpect(jsonPath("$.data").doesNotExist());
        assertThat(friendships.count()).isZero();
    }

    @Test
    void returnsGenericServerMessageForUnexpectedFailure() throws Exception {
        Users requester = fixture.user("requester");
        Users receiver = fixture.user("receiver");
        doThrow(new IllegalStateException("internal implementation detail"))
                .when(facade).requestFriend(anyLong(), anyLong());

        mockMvc.perform(request(requester, receiver))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value(SERVER_ERROR_MESSAGE))
                .andExpect(jsonPath("$.data").doesNotExist());
        assertThat(friendships.count()).isZero();
    }

    @Test
    void returnsGenericServerMessageForInterruptedLock() throws Exception {
        Users requester = fixture.user("requester");
        Users receiver = fixture.user("receiver");
        doThrow(new CommException(CommResponseStatus.LOCK_INTERRUPTED))
                .when(facade).requestFriend(anyLong(), anyLong());

        mockMvc.perform(request(requester, receiver))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("LOCK_INTERRUPTED"))
                .andExpect(jsonPath("$.message").value(SERVER_ERROR_MESSAGE))
                .andExpect(jsonPath("$.data").doesNotExist());
        assertThat(friendships.count()).isZero();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
            Users requester, Users receiver) throws Exception {
        return post("/api/friends/request")
                .header("X-USER-ID", requester.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new FriendRequest(receiver.getId())));
    }
}
