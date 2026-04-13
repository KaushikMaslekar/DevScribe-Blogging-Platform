package com.devscribe.dto.post;

import java.time.OffsetDateTime;

public record TrashPostResponse(
        Long id,
        String slug,
        String title,
        OffsetDateTime deletedAt,
        String deletedBy,
        OffsetDateTime updatedAt
        ) {

}
