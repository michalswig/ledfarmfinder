package com.mike.leadfarmfinder.repository;

public record SerpQueryStatsProjection(
        long runsCount,
        long totalAccepted,
        long totalRejected,
        long totalPagesVisited,
        long totalRawUrls
) {}