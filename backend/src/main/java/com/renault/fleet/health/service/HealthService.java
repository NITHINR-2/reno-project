package com.renault.fleet.health.service;

import com.renault.fleet.health.dto.HealthResponse;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    public static final String UP = "UP";
    public static final String DOWN = "DOWN";

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);

    private final JdbcTemplate jdbcTemplate;
    private final String version;

    public HealthService(JdbcTemplate jdbcTemplate, @Value("${fleet.version:unknown}") String version) {
        this.jdbcTemplate = jdbcTemplate;
        this.version = version;
    }

    public HealthResponse check() {
        String database = databaseStatus();
        return new HealthResponse(UP.equals(database) ? UP : DOWN, database, version, Instant.now());
    }

    private String databaseStatus() {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Integer.valueOf(1).equals(one) ? UP : DOWN;
        } catch (RuntimeException ex) {
            log.warn("Database health check failed: {}", ex.getMessage());
            return DOWN;
        }
    }
}
