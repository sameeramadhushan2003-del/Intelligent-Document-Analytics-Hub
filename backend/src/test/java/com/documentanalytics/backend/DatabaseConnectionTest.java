
package com.documentanalytics.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class DatabaseConnectionTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldConnectToPostgreSQL() {

        Integer result = jdbcTemplate.queryForObject(
            "SELECT 1",
            Integer.class
        );

        assertEquals(1, result);
    }

    @Test
    void shouldConnectToCorrectDatabase() {

        String databaseName = jdbcTemplate.queryForObject(
            "SELECT current_database()",
            String.class
        );

        assertEquals("document_analytics_hub", databaseName);
    }
}
