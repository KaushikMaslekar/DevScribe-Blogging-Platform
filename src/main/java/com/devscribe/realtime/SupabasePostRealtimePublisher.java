package com.devscribe.realtime;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class SupabasePostRealtimePublisher implements PostRealtimePublisher {

    private static final Logger logger = LoggerFactory.getLogger(SupabasePostRealtimePublisher.class);

    private final RealtimeProperties realtimeProperties;
    private final RestClient.Builder restClientBuilder;

    public SupabasePostRealtimePublisher(
            RealtimeProperties realtimeProperties,
            RestClient.Builder restClientBuilder
    ) {
        this.realtimeProperties = realtimeProperties;
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public void publishPostEvent(PostRealtimeEvent event) {
        if (!realtimeProperties.isEnabled()) {
            return;
        }

        if (!StringUtils.hasText(realtimeProperties.getSupabaseUrl())
                || !StringUtils.hasText(realtimeProperties.getServiceRoleKey())) {
            logger.debug("Realtime enabled but Supabase URL/key missing; skipping event publication.");
            return;
        }

        CompletableFuture.runAsync(() -> publishSync(event));
    }

    private void publishSync(PostRealtimeEvent event) {
        try {
            RestClient client = restClientBuilder
                    .baseUrl(realtimeProperties.getSupabaseUrl())
                    .build();

            String eventName = "post." + event.eventType().name().toLowerCase();

            Map<String, Object> payload = Map.of(
                    "messages", List.of(
                            Map.of(
                                    "topic", realtimeProperties.getChannel(),
                                    "event", eventName,
                                    "payload", Map.of(
                                            "postId", event.postId(),
                                            "slug", event.slug(),
                                            "status", event.status().name(),
                                            "occurredAt", event.occurredAt().toString()
                                    ),
                                    "private", false
                            )
                    )
            );

            client.post()
                    .uri("/realtime/v1/api/broadcast")
                    .header("apikey", realtimeProperties.getServiceRoleKey())
                    .header("Authorization", "Bearer " + realtimeProperties.getServiceRoleKey())
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception exception) {
            logger.warn("Failed to publish realtime event for post {}", event.postId(), exception);
        }
    }
}
