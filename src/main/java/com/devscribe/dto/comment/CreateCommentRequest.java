package com.devscribe.dto.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        @NotBlank(message = "Comment content cannot be blank")
        @Size(min = 1, max = 5000, message = "Comment must be between 1 and 5000 characters")
        String content,

        Long parentCommentId // optional, for replies
) {
}

