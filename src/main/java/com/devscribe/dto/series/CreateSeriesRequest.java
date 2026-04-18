package com.devscribe.dto.series;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSeriesRequest(
        @NotBlank
        @Size(max = 255)
        String title,
        @Size(max = 2000)
        String description
) {

}

