package com.devscribe.security;

import java.io.IOException;
import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.devscribe.util.StructuredLogger;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Logging filter that captures request/response details including timing,
 * status codes, and headers for observability and debugging.
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final StructuredLogger log = StructuredLogger.create(RequestLoggingFilter.class);

    // Exclude health/metrics endpoints from verbose logging
    private static final String[] EXCLUDED_PATHS = {"/health", "/metrics", "/actuator"};

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip verbose logging for health/metrics endpoints
        if (shouldSkipLogging(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        long startTime = System.currentTimeMillis();
        Instant requestTime = Instant.now();

        // Wrap response to capture content
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        try {
            // Log incoming request
            logRequest(request, requestTime);

            filterChain.doFilter(request, responseWrapper);

            // Log outgoing response
            long duration = System.currentTimeMillis() - startTime;
            logResponse(request, responseWrapper, duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Request processing error: {} {} in {}ms", request.getMethod(), path, duration, e);
            throw e;
        } finally {
            // Copy response content back to original response
            responseWrapper.copyBodyToResponse();
        }
    }

    private void logRequest(HttpServletRequest request, Instant timestamp) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        String queryString = request.getQueryString();
        String clientIp = getClientIp(request);

        log.info("Incoming request: {} {} from {} at {}",
                method,
                path + (queryString != null ? "?" + queryString : ""),
                clientIp,
                timestamp);

        // Log important headers for debugging
        String userAgent = request.getHeader("User-Agent");
        String authorization = request.getHeader("Authorization");
        if (userAgent != null) {
            log.debug("User-Agent: {}", userAgent.substring(0, Math.min(100, userAgent.length())));
        }
        if (authorization != null) {
            log.debug("Authorization: Bearer ***");
        }
    }

    private void logResponse(HttpServletRequest request, ContentCachingResponseWrapper response, long duration) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        int status = response.getStatus();
        String statusCategory = getStatusCategory(status);

        log.info("Outgoing response: {} {} -> {} ({}) in {}ms",
                method,
                path,
                status,
                statusCategory,
                duration);

        // Log response headers for debugging (selective)
        String contentType = response.getContentType();
        if (contentType != null) {
            log.debug("Content-Type: {}", contentType);
        }

        // Warn on slow responses
        if (duration > 5000) {
            log.warn("Slow response detected: {} {} took {}ms", method, path, duration);
        }

        // Error log on 4xx/5xx
        if (status >= 400) {
            int contentLength = response.getContentSize();
            if (contentLength > 0) {
                log.warn("Error response body size: {} bytes", contentLength);
            }
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = request.getHeader("X-Real-IP");
        }
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = request.getRemoteAddr();
        }
        // X-Forwarded-For can contain multiple IPs, take the first one
        if (clientIp != null && clientIp.contains(",")) {
            clientIp = clientIp.split(",")[0].trim();
        }
        return clientIp;
    }

    private String getStatusCategory(int status) {
        if (status < 300) {
            return "OK";
        }
        if (status < 400) {
            return "REDIRECT";
        }
        if (status < 500) {
            return "CLIENT_ERROR";
        }
        return "SERVER_ERROR";
    }

    private boolean shouldSkipLogging(String path) {
        for (String excludedPath : EXCLUDED_PATHS) {
            if (path.startsWith(excludedPath)) {
                return true;
            }
        }
        return false;
    }
}
