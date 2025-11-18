package com.mike.leadfarmfinder.dto;

public record FarmClassificationResult(
        boolean isFarm,
        boolean isSeasonalJobs,
        String reason,
        String mainContactUrl
) {
}
