package com.devscribe.exception;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.devscribe.util.CorrelationIdUtil;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        Map<String, Object> payload = new HashMap<>();
        payload.put("timestamp", OffsetDateTime.now());
        payload.put("status", status.value());
        payload.put("error", status.getReasonPhrase());
        payload.put("message", exception.getReason());
        payload.put("correlationId", CorrelationIdUtil.getOrGenerateCorrelationId());

        return ResponseEntity.status(status).body(payload);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException exception) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("timestamp", OffsetDateTime.now());
        payload.put("status", HttpStatus.BAD_REQUEST.value());
        payload.put("error", HttpStatus.BAD_REQUEST.getReasonPhrase());
        payload.put("message", "Validation failed");
        payload.put("correlationId", CorrelationIdUtil.getOrGenerateCorrelationId());

        Map<String, String> fieldErrors = new HashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(err -> fieldErrors.put(err.getField(), err.getDefaultMessage()));
        payload.put("details", fieldErrors);

        return ResponseEntity.badRequest().body(payload);
    }
}
