package com.barofarm.sample.health;

import java.time.Instant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DbHealthController {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    public DbHealthController(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/db-health")
    public ResponseEntity<DbHealthResponse> dbHealth() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        boolean mysqlUp = (result != null && result == 1);

        boolean redisUp = false;
        try {
            var connectionFactory = redisTemplate.getConnectionFactory();
            String pong =
                    connectionFactory != null ? connectionFactory.getConnection().ping() : null;
            redisUp = pong != null && "PONG".equalsIgnoreCase(pong);
        } catch (Exception ignored) {
            redisUp = false;
        }

        String overall = mysqlUp && redisUp ? "UP" : "DOWN";

        return ResponseEntity.ok(
                new DbHealthResponse(
                        overall, mysqlUp, redisUp, Instant.now().toString()));
    }

    public record DbHealthResponse(
            String status, boolean mysqlUp, boolean redisUp, String timestamp) {}
}
