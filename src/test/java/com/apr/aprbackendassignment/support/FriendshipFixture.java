package com.apr.aprbackendassignment.support;

import com.apr.aprbackendassignment.model.constant.FRIENDSHIP_STATUS;
import com.apr.aprbackendassignment.model.entity.Friendship;
import com.apr.aprbackendassignment.model.entity.Users;
import com.apr.aprbackendassignment.repository.FriendshipJPARepository;
import com.apr.aprbackendassignment.repository.UsersJPARepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

public final class FriendshipFixture {

    private final UsersJPARepository users;
    private final FriendshipJPARepository friendships;
    private final JdbcTemplate jdbc;

    public FriendshipFixture(UsersJPARepository users, FriendshipJPARepository friendships, JdbcTemplate jdbc) {
        this.users = users;
        this.friendships = friendships;
        this.jdbc = jdbc;
    }

    public Users user(String name) {
        return users.saveAndFlush(Users.create(name));
    }

    public Friendship relationship(Users requester, Users receiver, FRIENDSHIP_STATUS status) {
        LocalDateTime now = LocalDateTime.now();
        Friendship friendship = Friendship.request(requester, receiver, now);
        switch (status) {
            case REQUESTED -> { }
            case ACCEPTED -> friendship.accept(receiver.getId(), now);
            case REJECTED -> friendship.reject(receiver.getId());
            default -> throw new IllegalArgumentException("Unsupported fixture status: " + status);
        }
        return friendships.saveAndFlush(friendship);
    }

    public Friendship relationshipAt(Users requester, Users receiver, FRIENDSHIP_STATUS status, LocalDateTime time) {
        Friendship friendship = relationship(requester, receiver, status);
        // Historical fixtures must bypass JPA auditing without adding test-only entity setters.
        jdbc.update("UPDATE friendship SET created_time = ?, updated_time = ?, requested_at = ?, approved_at = ? WHERE id = ?",
                time, time, time, status == FRIENDSHIP_STATUS.ACCEPTED ? time : null, friendship.getId());
        return friendship;
    }
}
