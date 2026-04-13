package com.devscribe.security;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private static final Pattern POSTS_PUBLISH_PATH = Pattern.compile("^/api/posts/\\d+/publish$");

    // Token bucket state: key = clientId, value = {tokensRemaining, lastRefillTime}
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    // Auth endpoints: 5 requests per minute
    private static final int AUTH_LIMIT = 5;
    private static final long AUTH_WINDOW_MS = TimeUnit.MINUTES.toMillis(1);

    // Post creation: 20 requests per minute
    private static final int POST_LIMIT_WRITER = 20;
    private static final int POST_LIMIT_EDITOR = 40;
    private static final int POST_LIMIT_ADMIN = 80;
    private static final long POST_WINDOW_MS = TimeUnit.MINUTES.toMillis(1);

    // Post publishing: tighter for writers, broader for editorial/admin tiers
    private static final int PUBLISH_LIMIT_WRITER = 10;
    private static final int PUBLISH_LIMIT_EDITOR = 30;
    private static final int PUBLISH_LIMIT_ADMIN = 60;
    private static final long PUBLISH_WINDOW_MS = TimeUnit.MINUTES.toMillis(1);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        String tier = resolveTier();
        String clientId = getClientKey(request, tier);

        // Apply rate limiting to sensitive endpoints
        if (isAuthEndpoint(path)) {
            if (!checkRateLimit(clientId, AUTH_LIMIT, AUTH_WINDOW_MS)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Rate limit exceeded. Maximum " + AUTH_LIMIT + " requests per minute.\"}");
                return;
            }
        } else if (isPostCreationEndpoint(path, method)) {
            int postLimit = resolvePostCreateLimit(tier);
            if (!checkRateLimit(clientId + ":post-create", postLimit, POST_WINDOW_MS)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Rate limit exceeded. Maximum " + postLimit + " post create requests per minute.\"}");
                return;
            }
        } else if (isPublishEndpoint(path, method)) {
            int publishLimit = resolvePublishLimit(tier);
            if (!checkRateLimit(clientId + ":publish", publishLimit, PUBLISH_WINDOW_MS)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Rate limit exceeded. Maximum " + publishLimit + " publish requests per minute.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean checkRateLimit(String clientId, int limit, long windowMs) {
        TokenBucket bucket = buckets.computeIfAbsent(clientId, k -> new TokenBucket(limit, windowMs));
        return bucket.tryConsume();
    }

    private String getClientKey(HttpServletRequest request, String tier) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getName() != null && !"anonymousUser".equals(authentication.getName())) {
            return tier + ":" + authentication.getName();
        }

        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = request.getRemoteAddr();
        }
        return tier + ":" + clientIp;
    }

    private boolean isAuthEndpoint(String path) {
        return path.equals(AUTH_LOGIN_PATH) || path.equals(AUTH_REGISTER_PATH);
    }

    private boolean isPostCreationEndpoint(String path, String method) {
        return "POST".equals(method) && path.equals(POSTS_CREATE_PATH);
    }

    private boolean isPublishEndpoint(String path, String method) {
        return "POST".equals(method) && POSTS_PUBLISH_PATH.matcher(path).matches();
    }

    private String resolveTier() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return "ANON";
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (isAdmin) {
            return "ADMIN";
        }

        boolean isEditor = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_EDITOR".equals(authority.getAuthority()));
        if (isEditor) {
            return "EDITOR";
        }

        boolean isWriter = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_WRITER".equals(authority.getAuthority()));
        if (isWriter) {
            return "WRITER";
        }

        return "ANON";
    }

    private int resolvePostCreateLimit(String tier) {
        return switch (tier) {
            case "ADMIN" ->
                POST_LIMIT_ADMIN;
            case "EDITOR" ->
                POST_LIMIT_EDITOR;
            default ->
                POST_LIMIT_WRITER;
        };
    }

    private int resolvePublishLimit(String tier) {
        return switch (tier) {
            case "ADMIN" ->
                PUBLISH_LIMIT_ADMIN;
            case "EDITOR" ->
                PUBLISH_LIMIT_EDITOR;
            default ->
                PUBLISH_LIMIT_WRITER;
        };
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
