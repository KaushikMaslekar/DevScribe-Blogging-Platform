package com.devscribe.security;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.devscribe.util.CorrelationIdUtil;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filter that manages correlation IDs for request tracing and debugging.
 * Extracts correlation ID from request headers or generates a new one. Sets it
 * in MDC and response headers for distributed tracing.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(CorrelationIdUtil.getCorrelationIdHeader());

        if (!StringUtils.hasText(correlationId)) {
            correlationId = CorrelationIdUtil.generateCorrelationId();
        } else {
            CorrelationIdUtil.setCorrelationId(correlationId);
        }

        try {
            CorrelationIdUtil.setCorrelationId(correlationId);
            response.setHeader(CorrelationIdUtil.getCorrelationIdHeader(), correlationId);
            filterChain.doFilter(request, response);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }
}
