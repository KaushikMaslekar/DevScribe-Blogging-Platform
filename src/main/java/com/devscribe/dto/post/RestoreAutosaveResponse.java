package com.devscribe.dto.post;

import java.time.OffsetDateTime;
import java.util.List;

public record RestoreAutosaveResponse(
        Long postId,
        String slug,
        Long autosaveRevision,
        String title,
        String excerpt,
        String markdownContent,
        OffsetDateTime scheduledPublishAt,
        List<String> tags,
        OffsetDateTime restoredAt
        ) {

}
