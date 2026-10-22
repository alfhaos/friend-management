package com.apr.aprbackendassignment.sampleData;

import com.apr.aprbackendassignment.AprBackendAssignmentApplication;
import com.apr.aprbackendassignment.model.constant.FRIENDSHIP_STATUS;
import com.apr.aprbackendassignment.model.entity.Friendship;
import com.apr.aprbackendassignment.model.entity.Users;
import com.apr.aprbackendassignment.repository.FriendshipJPARepository;
import com.apr.aprbackendassignment.repository.UsersJPARepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/** Explicit development tool; never discovered or executed by the test task. */
public final class SampleDataInsert {

    private static final Logger log = LoggerFactory.getLogger(SampleDataInsert.class);
    private static final int DEFAULT_COUNT = 100;
    private static final int[] DAYS_AGO = {0, 6, 29, 89, 120};

    private SampleDataInsert() {
    }

    public static void main(String[] args) {
        int count = args.length == 0 ? DEFAULT_COUNT : Integer.parseInt(args[0]);
        if (count < 1 || count > 10000) {
            throw new IllegalArgumentException("Sample count must be between 1 and 10000");
        }

        try (var context = new SpringApplicationBuilder(AprBackendAssignmentApplication.class)
                .web(WebApplicationType.NONE).run()) {
            var users = context.getBean(UsersJPARepository.class);
            var friendships = context.getBean(FriendshipJPARepository.class);
            var jdbc = context.getBean(JdbcTemplate.class);
            var transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));

            transactions.executeWithoutResult(status -> {
                Users receiver = users.saveAndFlush(Users.create("sample-receiver"));
                LocalDateTime now = LocalDateTime.now();
                for (int i = 0; i < count; i++) {
                    Users requester = users.saveAndFlush(Users.create("sample-user-" + i));
                    Friendship friendship = friendships.saveAndFlush(
                            Friendship.create(requester, receiver, FRIENDSHIP_STATUS.REQUESTED));
                    LocalDateTime requestedAt = now.minusDays(DAYS_AGO[i % DAYS_AGO.length]);
                    jdbc.update("UPDATE friendship SET created_time = ?, updated_time = ? WHERE id = ?",
                            requestedAt, requestedAt, friendship.getId());
                }
                log.info("Created {} sample requests for receiver ID {}", count, receiver.getId());
            });
        }
    }
}
