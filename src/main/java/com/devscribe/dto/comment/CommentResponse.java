package com.devscribe.dto.comment;

import java.time.OffsetDateTime;

public record CommentResponse(
        Long id,
        Long postId,
        Long parentCommentId,
        AuthorInfo author,
        String content,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        boolean isAuthor
) {
}

