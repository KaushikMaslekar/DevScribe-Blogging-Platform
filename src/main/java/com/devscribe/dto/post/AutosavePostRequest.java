package com.devscribe.dto.post;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AutosavePostRequest(
        Long postId,
        @NotNull
        @PositiveOrZero
        Long clientRevision,
        @Size(max = 255)
        String title,
        @Size(max = 1000)
        String excerpt,
        String markdownContent,
        @FutureOrPresent
        OffsetDateTime scheduledPublishAt,
        List<@Size(min = 1, max = 80) String> tags
        ) {

}
