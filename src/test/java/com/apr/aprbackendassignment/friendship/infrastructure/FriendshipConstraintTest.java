package com.apr.aprbackendassignment.friendship.infrastructure;

import com.apr.aprbackendassignment.user.domain.Users;
import com.apr.aprbackendassignment.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static com.apr.aprbackendassignment.friendship.domain.FRIENDSHIP_STATUS.REJECTED;
import static com.apr.aprbackendassignment.friendship.domain.FRIENDSHIP_STATUS.REQUESTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FriendshipConstraintTest extends IntegrationTestSupport {

    @Test
    void databaseHasBothDirectionalAndActivePairUniqueIndexes() {
        var directionalIndexes = jdbc.queryForList("""
                SELECT indexname FROM pg_indexes
                WHERE schemaname = 'public' AND tablename = 'friendship'
                  AND indexdef LIKE '%(requester_id, receiver_id)%'
                  AND indexdef LIKE 'CREATE UNIQUE INDEX%'
                  AND indexname <> 'ux_friendship_active_pair'
                """, String.class);
        var activeIndexes = jdbc.queryForList("""
                SELECT indexname FROM pg_indexes
                WHERE schemaname = 'public' AND tablename = 'friendship'
                """, String.class);

        assertThat(directionalIndexes).hasSize(1);
        assertThat(activeIndexes).contains("ux_friendship_active_pair");
    }


    @Test
    void databaseRejectsReverseActiveRelationship() {
        Users first = fixture.user("first");
        Users second = fixture.user("second");
        fixture.relationship(first, second, REQUESTED);

        assertThatThrownBy(() -> insertRelationship(second, first, "REQUESTED"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(friendships.count()).isEqualTo(1);
    }

    @Test
    void databaseRejectsSameDirectionDuplicateRequest() {
        Users requester = fixture.user("requester");
        Users receiver = fixture.user("receiver");
        insertRelationship(requester, receiver, "REQUESTED");

        assertThatThrownBy(() -> insertRelationship(requester, receiver, "REQUESTED"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(friendships.count()).isEqualTo(1);
        assertThat(friendships.findByRequesterIdAndReceiverId(requester.getId(), receiver.getId()).getStatus())
                .isEqualTo(REQUESTED);
    }

    @Test
    void databaseAllowsReverseRequestBesideRejectedHistory() {
        Users first = fixture.user("first");
        Users second = fixture.user("second");
        var rejected = fixture.relationship(first, second, REJECTED);

        insertRelationship(second, first, "REQUESTED");

        assertThat(friendships.count()).isEqualTo(2);
        assertThat(friendships.findById(rejected.getId()).orElseThrow().getStatus()).isEqualTo(REJECTED);
        assertThat(friendships.findByRequesterIdAndReceiverId(second.getId(), first.getId()).getStatus())
                .isEqualTo(REQUESTED);
    }

    private void insertRelationship(Users requester, Users receiver, String status) {
        jdbc.update("""
                INSERT INTO friendship (id, requester_id, receiver_id, status, created_time, updated_time)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), requester.getId(), receiver.getId(), status);
    }
}
