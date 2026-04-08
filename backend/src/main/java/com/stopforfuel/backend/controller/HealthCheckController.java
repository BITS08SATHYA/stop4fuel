package com.stopforfuel.backend.controller;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthCheckController {

    private final DataSource dataSource;

    @GetMapping
    public HealthStatus check() {
        HealthStatus status = new HealthStatus();
        status.setTimestamp(Instant.now().toString());

        long start = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(5);
            status.setLatencyMs(System.currentTimeMillis() - start);
            status.setDatabase(valid ? "UP" : "DOWN");
            status.setStatus(valid ? "UP" : "DEGRADED");
        } catch (Exception e) {
            status.setLatencyMs(System.currentTimeMillis() - start);
            status.setDatabase("DOWN");
            status.setStatus("DOWN");
        }

        return status;
    }

    @Getter
    @Setter
    public static class HealthStatus {
        private String status;
        private String database;
        private long latencyMs;
        private String timestamp;
    }
}
