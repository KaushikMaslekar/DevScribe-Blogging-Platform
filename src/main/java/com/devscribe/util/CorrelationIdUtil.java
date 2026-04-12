package com.devscribe.util;

import java.util.UUID;

import org.slf4j.MDC;

/**
 * Utility for managing correlation IDs across requests. Correlation IDs are
 * used for request tracing and debugging in distributed systems.
 */
public class CorrelationIdUtil {

    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    /**
     * Generate a new correlation ID if not already set in MDC.
     */
    public static String getOrGenerateCorrelationId() {
        String correlationId = MDC.get(CORRELATION_ID_KEY);
        if (correlationId == null) {
            correlationId = generateCorrelationId();
            setCorrelationId(correlationId);
        }
        return correlationId;
    }

    /**
     * Set the correlation ID in MDC.
     */
    public static void setCorrelationId(String correlationId) {
        if (correlationId != null) {
            MDC.put(CORRELATION_ID_KEY, correlationId);
        }
    }

    /**
     * Generate a new UUID-based correlation ID.
     */
    public static String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Get the correlation ID header name.
     */
    public static String getCorrelationIdHeader() {
        return CORRELATION_ID_HEADER;
    }

    /**
     * Clear the correlation ID from MDC.
     */
    public static void clearCorrelationId() {
        MDC.remove(CORRELATION_ID_KEY);
    }
}
