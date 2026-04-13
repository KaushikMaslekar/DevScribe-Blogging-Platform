package com.devscribe.dto.post;

import java.time.OffsetDateTime;
import java.util.List;

import com.devscribe.entity.PostStatus;

public record PostSummaryResponse(
        Long id,
        String slug,
        String title,
        String excerpt,
        String authorUsername,
        List<String> tags,
        PostStatus status,
        OffsetDateTime publishedAt,
        OffsetDateTime scheduledPublishAt,
        OffsetDateTime updatedAt,
        long likesCount,
        boolean likedByMe,
        boolean bookmarkedByMe,
        boolean authorFollowedByMe
        ) {

}
