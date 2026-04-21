package com.devscribe.dto.comment;

public record AuthorInfo(
        Long id,
        String username,
        String displayName,
        String avatarUrl
) {
}

