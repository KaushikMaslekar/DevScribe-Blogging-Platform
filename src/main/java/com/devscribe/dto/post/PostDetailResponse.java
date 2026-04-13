package com.devscribe.dto.post;

import java.time.OffsetDateTime;
import java.util.List;

import com.devscribe.entity.PostStatus;

public record PostDetailResponse(
        Long id,
        String slug,
        String title,
        String excerpt,
        String markdownContent,
        String authorUsername,
        PostStatus status,
        OffsetDateTime publishedAt,
        OffsetDateTime scheduledPublishAt,
        OffsetDateTime updatedAt,
        List<String> tags,
        long views,
        long likesCount,
        boolean likedByMe,
        boolean bookmarkedByMe,
        boolean authorFollowedByMe
        ) {

}
