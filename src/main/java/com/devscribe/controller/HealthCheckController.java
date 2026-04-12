package com.devscribe.controller;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devscribe.repository.PostRepository;
import com.devscribe.util.CorrelationIdUtil;
import com.devscribe.util.StructuredLogger;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Health check and readiness probe endpoints for observability and
 * orchestration. Used by load balancers, Kubernetes, and monitoring systems.
 */
@RestController
@RequestMapping("/health")
public class HealthCheckController {

    private static final StructuredLogger log = StructuredLogger.create(HealthCheckController.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired(required = false)
    private PostRepository postRepository;

    /**
     * Simple alive check - returns 200 if server is running. Used by load
     * balancers for basic health monitoring.
     */
    @GetMapping("/live")
    public ResponseEntity<Map<String, Object>> liveness() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", OffsetDateTime.now());
        response.put("correlationId", CorrelationIdUtil.getOrGenerateCorrelationId());

        log.info("Liveness probe successful");
        return ResponseEntity.ok(response);
    }

    /**
     * Readiness check - verifies database connectivity and critical services.
     * Returns 200 only if the service is ready to accept traffic.
     */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> readiness() {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> checks = new HashMap<>();

        // Check database connectivity
        boolean dbReady = checkDatabaseReady();
        checks.put("database", dbReady ? "UP" : "DOWN");

        boolean allReady = dbReady;
        response.put("status", allReady ? "UP" : "DOWN");
        response.put("checks", checks);
        response.put("timestamp", OffsetDateTime.now());
        response.put("correlationId", CorrelationIdUtil.getOrGenerateCorrelationId());

        if (!allReady) {
            log.warn("Readiness probe failed: {}", checks);
            return ResponseEntity.status(503).body(response);
        }

        log.info("Readiness probe successful");
        return ResponseEntity.ok(response);
    }

    /**
     * Detailed health status with component information. Useful for dashboards
     * and comprehensive monitoring.
     */
    @GetMapping("/detailed")
    public ResponseEntity<Map<String, Object>> detailedHealth() {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> details = new HashMap<>();

        // Check database
        long dbCheckStart = System.currentTimeMillis();
        boolean dbReady = checkDatabaseReady();
        long dbCheckDuration = System.currentTimeMillis() - dbCheckStart;
        details.put("database", Map.of(
                "status", dbReady ? "UP" : "DOWN",
                "responseTimeMs", dbCheckDuration
        ));

        // JVM info
        Runtime runtime = Runtime.getRuntime();
        details.put("jvm", Map.of(
                "totalMemoryMb", runtime.totalMemory() / (1024 * 1024),
                "freeMemoryMb", runtime.freeMemory() / (1024 * 1024),
                "maxMemoryMb", runtime.maxMemory() / (1024 * 1024)
        ));

        response.put("status", dbReady ? "UP" : "DOWN");
        response.put("components", details);
        response.put("timestamp", OffsetDateTime.now());
        response.put("correlationId", CorrelationIdUtil.getOrGenerateCorrelationId());

        return ResponseEntity.ok(response);
    }

    private boolean checkDatabaseReady() {
        try {
            // Execute a simple query to verify database connectivity
            entityManager.createQuery("SELECT 1", Integer.class)
                    .setMaxResults(1)
                    .getSingleResult();
            return true;
        } catch (Exception e) {
            log.error("Database health check failed: {}", e.getMessage());
            return false;
        }
    }
}
