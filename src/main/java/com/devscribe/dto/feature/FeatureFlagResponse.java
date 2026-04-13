package com.devscribe.dto.feature;

public record FeatureFlagResponse(
        String key,
        String description,
        boolean enabled,
        int rolloutPercentage,
        boolean enabledForMe
        ) {

}
