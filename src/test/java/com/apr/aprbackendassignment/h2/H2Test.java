package com.apr.aprbackendassignment.h2;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Slf4j
public class H2Test {

    @Autowired
    private DataSource dataSource;

    @Test
    public void testH2Connection() throws Exception {
        try(Connection conn = dataSource.getConnection()) {
            log.info("H2 Database Connection Successful: {}", conn);
            assertNotNull(conn);
            assertFalse(conn.isClosed());
        }
    }
}
