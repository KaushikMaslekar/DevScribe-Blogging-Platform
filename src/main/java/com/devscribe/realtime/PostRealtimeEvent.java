package com.devscribe.realtime;

import java.time.OffsetDateTime;

import com.devscribe.entity.PostStatus;

public record PostRealtimeEvent(
        Long postId,
        String slug,
        PostStatus status,
        PostRealtimeEventType eventType,
        OffsetDateTime occurredAt
        ) {

}
