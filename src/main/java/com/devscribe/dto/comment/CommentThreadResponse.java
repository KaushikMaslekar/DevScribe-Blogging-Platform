package com.devscribe.dto.comment;

import java.util.List;

public record CommentThreadResponse(
        CommentResponse comment,
        List<CommentThreadResponse> replies
) {
}

