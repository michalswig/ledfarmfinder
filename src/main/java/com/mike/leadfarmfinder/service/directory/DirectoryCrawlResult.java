package com.mike.leadfarmfinder.service.directory;

public record DirectoryCrawlResult(
        String sourceName,
        int urlsFetched,
        int urlsSkippedDuplicate,
        int urlsProcessed,
        int urlsRejectedByClassifier,
        int urlsScrapedOk,
        int urlsScrapedError,
        long durationMs
) {
    public static DirectoryCrawlResult empty(String sourceName, long durationMs) {
        return new DirectoryCrawlResult(sourceName, 0, 0, 0, 0, 0, 0, durationMs);
    }
}