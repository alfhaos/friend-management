package com.apr.aprbackendassignment.schema;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostgreSqlSchemaScriptTest {

    @Test
    void appliesReferenceSchemaToAnEmptyPostgreSqlDatabase() throws Exception {
        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();

            try (Connection connection = DriverManager.getConnection(
                    database.getJdbcUrl(), database.getUsername(), database.getPassword())) {
                ScriptUtils.executeSqlScript(connection, new FileSystemResource("sql/postgresql-schema.sql"));

                assertThat(count(connection, "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name IN ('users', 'friendship')"))
                        .isEqualTo(2);
                assertThat(count(connection, "SELECT COUNT(*) FROM pg_constraint WHERE conname IN "
                        + "('friendship_status_check', 'friendship_requester_id_receiver_id_key', "
                        + "'fk_friendship_receiver', 'fk_friendship_requester')")).isEqualTo(4);
                assertThat(count(connection, "SELECT COUNT(*) FROM pg_indexes "
                        + "WHERE schemaname = 'public' AND indexname = 'ux_friendship_active_pair'"))
                        .isEqualTo(1);

                insertUsers(connection, 5);
                insertFriendship(connection, "REQUESTED", 1, 2);
                insertFriendship(connection, "ACCEPTED", 1, 3);
                insertFriendship(connection, "REJECTED", 1, 4);
                insertFriendship(connection, "BLOCKED", 1, 5);

                assertThat(count(connection, "SELECT COUNT(*) FROM friendship")).isEqualTo(4);
                assertThatThrownBy(() -> insertFriendship(connection, "UNKNOWN", 2, 3))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> insertFriendship(connection, "REQUESTED", 2, 1))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> insertFriendship(connection, "REJECTED", 99, 4))
                        .isInstanceOf(SQLException.class);
            }
        }
    }

    private int count(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getInt(1);
        }
    }

    private void insertUsers(Connection connection, int count) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO users (user_name) VALUES (?)")) {
            for (int index = 1; index <= count; index++) {
                statement.setString(1, "user-" + index);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertFriendship(Connection connection, String status, long requesterId, long receiverId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO friendship "
                + "(id, status, requester_id, receiver_id) VALUES (?, ?, ?, ?)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, status);
            statement.setLong(3, requesterId);
            statement.setLong(4, receiverId);
            statement.executeUpdate();
        }
    }
}
