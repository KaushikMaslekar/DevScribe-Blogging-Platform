package com.devscribe.dto.post;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePostRequest(
        @NotBlank
        @Size(max = 255)
        String title,
        @Size(max = 1000)
        String excerpt,
        @NotBlank
        String markdownContent,
        @FutureOrPresent
        OffsetDateTime scheduledPublishAt,
        List<@Size(min = 1, max = 80) String> tags
        ) {

}
