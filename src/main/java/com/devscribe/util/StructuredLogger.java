package com.devscribe.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Structured logging utility that automatically includes correlation IDs and
 * other context in all log messages.
 */
public class StructuredLogger {

    private final Logger logger;

    public StructuredLogger(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
    }

    public static StructuredLogger create(Class<?> clazz) {
        return new StructuredLogger(clazz);
    }

    public void info(String message) {
        logger.info(buildMessage(message));
    }

    public void info(String message, Object... args) {
        logger.info(buildMessage(message), args);
    }

    public void debug(String message) {
        logger.debug(buildMessage(message));
    }

    public void debug(String message, Object... args) {
        logger.debug(buildMessage(message), args);
    }

    public void warn(String message) {
        logger.warn(buildMessage(message));
    }

    public void warn(String message, Object... args) {
        logger.warn(buildMessage(message), args);
    }

    public void warn(String message, Throwable t) {
        logger.warn(buildMessage(message), t);
    }

    public void error(String message) {
        logger.error(buildMessage(message));
    }

    public void error(String message, Object... args) {
        logger.error(buildMessage(message), args);
    }

    public void error(String message, Throwable t) {
        logger.error(buildMessage(message), t);
    }

    public void error(String message, Throwable t, Object... args) {
        logger.error(buildMessage(message), args);
        logger.error("Exception details:", t);
    }

    /**
     * Build message with correlation ID context. Format: [correlationId]
     * message
     */
    private String buildMessage(String message) {
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            return "[" + correlationId + "] " + message;
        }
        return message;
    }
}
