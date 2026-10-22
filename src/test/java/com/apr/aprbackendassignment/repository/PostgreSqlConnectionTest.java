package com.apr.aprbackendassignment.repository;

import com.apr.aprbackendassignment.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class PostgreSqlConnectionTest extends IntegrationTestSupport {

    @Autowired
    private DataSource dataSource;

    @Test
    void connectsToIsolatedPostgreSqlDatabase() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.isClosed()).isFalse();
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
            assertThat(connection.getCatalog()).isEqualTo("apr_test");
        }
    }
}
