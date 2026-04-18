package com.devscribe.dto.series;

import java.time.OffsetDateTime;

public record SeriesSummaryResponse(
        Long id,
        String slug,
        String title,
        String description,
        String authorUsername,
        long postsCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

}

