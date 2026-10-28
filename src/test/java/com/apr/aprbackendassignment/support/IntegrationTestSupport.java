package com.apr.aprbackendassignment.support;

import com.apr.aprbackendassignment.friendship.infrastructure.FriendshipJPARepository;
import com.apr.aprbackendassignment.user.infrastructure.UsersJPARepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    // One pair per test JVM, so cached Spring contexts never point at stopped containers.
    // Testcontainers' resource reaper removes these non-reusable containers when the JVM exits.
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("apr_test")
                    .withUsername("apr_test")
                    .withPassword("apr_test");
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7.4.2-alpine").withExposedPorts(6379);

    static {
        Startables.deepStart(POSTGRES, REDIS).join();
    }

    @DynamicPropertySource
    static void configureInfrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    protected UsersJPARepository users;
    @Autowired
    protected FriendshipJPARepository friendships;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected RedissonClient redisson;

    protected FriendshipFixture fixture;

    @BeforeEach
    void prepareFixture() {
        clearInfrastructure();
        fixture = new FriendshipFixture(users, friendships, jdbc);
    }

    @AfterEach
    void cleanFixture() {
        clearInfrastructure();
    }

    private void clearInfrastructure() {
        // This connection and Redis client always belong to the private containers above.
        jdbc.execute("TRUNCATE TABLE friendship, users RESTART IDENTITY CASCADE");
        redisson.getKeys().flushdb();
    }
}
