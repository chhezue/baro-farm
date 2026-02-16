package com.barofarm.sample.health;

import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DbHealthController {

    private final JdbcTemplate jdbcTemplate;

    public DbHealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/db-health")
    public ResponseEntity<DbHealthResponse> dbHealth() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        boolean up = (result != null && result == 1);

        return ResponseEntity.ok(
                new DbHealthResponse(up ? "UP" : "DOWN", "baro-sample-db", Instant.now().toString()));
    }

    public record DbHealthResponse(String status, String component, String timestamp) {}
}

