package com.mike.leadfarmfinder.service.directory;

public record DirectoryCrawlResult(
        String sourceName,
        int urlsFetched,
        int urlsSkippedDuplicate,
        int urlsProcessed,
        int urlsScrapedOk,
        int urlsScrapedError,
        long durationMs
) {}