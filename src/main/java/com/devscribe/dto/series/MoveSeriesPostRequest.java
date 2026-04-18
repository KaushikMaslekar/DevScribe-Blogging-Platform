package com.devscribe.dto.series;

import jakarta.validation.constraints.Min;

public record MoveSeriesPostRequest(
        @Min(1)
        Long targetSeriesId,
        @Min(1)
        Integer sortOrder
) {

}

