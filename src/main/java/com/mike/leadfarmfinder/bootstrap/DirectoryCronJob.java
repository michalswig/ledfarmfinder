package com.mike.leadfarmfinder.bootstrap;

import com.mike.leadfarmfinder.service.directory.DirectoryCrawlResult;
import com.mike.leadfarmfinder.service.directory.DirectoryCrawlerService;
import com.mike.leadfarmfinder.service.directory.DirectoryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DirectoryCronJob {

    private final DirectoryCrawlerService crawlerService;
    private final DirectoryProperties directoryProperties;

    @Scheduled(cron = "${directory.cron:0 0 4 * * *}")
    public void run() {
        if (!directoryProperties.enabled()) {
            log.info("DirectoryCronJob: disabled, skipping");
            return;
        }

        log.info("DirectoryCronJob: starting, maxUrlsPerRun={}", directoryProperties.maxUrlsPerRun());

        try {
            List<DirectoryCrawlResult> results = crawlerService.crawlAll(directoryProperties.maxUrlsPerRun());

            log.info("DirectoryCronJob: finished. processed={}, ok={}, errors={}, skippedDuplicate={}",
                    results.stream().mapToInt(DirectoryCrawlResult::urlsProcessed).sum(),
                    results.stream().mapToInt(DirectoryCrawlResult::urlsScrapedOk).sum(),
                    results.stream().mapToInt(DirectoryCrawlResult::urlsScrapedError).sum(),
                    results.stream().mapToInt(DirectoryCrawlResult::urlsSkippedDuplicate).sum());

        } catch (Exception e) {
            log.error("DirectoryCronJob: failed: {}", e.getMessage(), e);
        }
    }
}