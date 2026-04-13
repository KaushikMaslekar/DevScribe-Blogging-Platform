package com.devscribe.dto.feature;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateFeatureFlagRequest(
        @NotBlank
        String key,
        String description,
        @NotNull
        Boolean enabled,
        @NotNull
        @Min(0)
        @Max(100)
        Integer rolloutPercentage
        ) {

}
