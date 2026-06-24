package com.lk.quantfund.controller;

import com.lk.quantfund.common.ApiResponse;
import com.lk.quantfund.constants.SystemConstants;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping(SystemConstants.API_PREFIX + "/health")
public class HealthController {

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;

    public HealthController(DataSource dataSource, RedisConnectionFactory redisConnectionFactory) {
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.success(Map.of(
                "application", SystemConstants.PROJECT_NAME,
                "status", "UP",
                "time", LocalDateTime.now()
        ));
    }

    @GetMapping("/dependencies")
    public ApiResponse<Map<String, Object>> dependencies() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("application", SystemConstants.PROJECT_NAME);
        result.put("time", LocalDateTime.now());
        result.put("database", checkDatabase());
        result.put("redis", checkRedis());
        return ApiResponse.success(result);
    }

    private Map<String, Object> checkDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            return Map.of(
                    "status", "UP",
                    "product", connection.getMetaData().getDatabaseProductName(),
                    "url", connection.getMetaData().getURL()
            );
        } catch (Exception exception) {
            return Map.of(
                    "status", "DOWN",
                    "message", exception.getMessage()
            );
        }
    }

    private Map<String, Object> checkRedis() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String pong = connection.ping();
            return Map.of(
                    "status", "UP",
                    "ping", pong == null ? "PONG" : pong
            );
        } catch (Exception exception) {
            return Map.of(
                    "status", "DOWN",
                    "message", exception.getMessage()
            );
        }
    }
}
