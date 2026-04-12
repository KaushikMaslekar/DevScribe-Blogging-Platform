package com.devscribe.security;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rate limiting filter to protect sensitive endpoints from brute force and DoS
 * attacks. Uses a simple token bucket algorithm with in-memory storage.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final String AUTH_LOGIN_PATH = "/api/auth/login";
    private static final String AUTH_REGISTER_PATH = "/api/auth/register";
    private static final String POSTS_CREATE_PATH = "/api/posts";

    // Token bucket state: key = clientId, value = {tokensRemaining, lastRefillTime}
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    // Auth endpoints: 5 requests per minute
    private static final int AUTH_LIMIT = 5;
    private static final long AUTH_WINDOW_MS = TimeUnit.MINUTES.toMillis(1);

    // Post creation: 20 requests per minute
    private static final int POST_LIMIT = 20;
    private static final long POST_WINDOW_MS = TimeUnit.MINUTES.toMillis(1);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        String clientId = getClientId(request);

        // Apply rate limiting to sensitive endpoints
        if (isAuthEndpoint(path)) {
            if (!checkRateLimit(clientId, AUTH_LIMIT, AUTH_WINDOW_MS)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Rate limit exceeded. Maximum " + AUTH_LIMIT + " requests per minute.\"}");
                return;
            }
        } else if (isPostCreationEndpoint(path, method)) {
            if (!checkRateLimit(clientId, POST_LIMIT, POST_WINDOW_MS)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Rate limit exceeded. Maximum " + POST_LIMIT + " requests per minute.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean checkRateLimit(String clientId, int limit, long windowMs) {
        TokenBucket bucket = buckets.computeIfAbsent(clientId, k -> new TokenBucket(limit, windowMs));
        return bucket.tryConsume();
    }

    private String getClientId(HttpServletRequest request) {
        // Use IP address as client identifier
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = request.getRemoteAddr();
        }
        return clientIp;
    }

    private boolean isAuthEndpoint(String path) {
        return path.equals(AUTH_LOGIN_PATH) || path.equals(AUTH_REGISTER_PATH);
    }

    private boolean isPostCreationEndpoint(String path, String method) {
        return "POST".equals(method) && path.equals(POSTS_CREATE_PATH);
    }

    /**
     * Simple token bucket implementation for rate limiting.
     */
    private static class TokenBucket {

        private final int capacity;
        private final long windowMs;
        private volatile int tokens;
        private volatile long lastRefillTime;

        TokenBucket(int capacity, long windowMs) {
            this.capacity = capacity;
            this.windowMs = windowMs;
            this.tokens = capacity;
            this.lastRefillTime = System.currentTimeMillis();
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens > 0) {
                tokens--;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long timePassed = now - lastRefillTime;

            if (timePassed >= windowMs) {
                // Window expired, reset tokens
                tokens = capacity;
                lastRefillTime = now;
            }
        }
    }
}
