package com.mike.leadfarmfinder.bootstrap;

import com.mike.leadfarmfinder.service.directory.DirectoryCrawlResult;
import com.mike.leadfarmfinder.service.directory.DirectoryCrawlerService;
import com.mike.leadfarmfinder.service.osm.OsmFarmSource;
import com.mike.leadfarmfinder.service.osm.OsmProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
public class OsmCronJob {

    private final DirectoryCrawlerService crawlerService;
    private final OsmFarmSource osmFarmSource;
    private final OsmProperties osmProperties;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(cron = "${osm.cron:0 0 2 * * SUN}")
    public void run() {
        if (!osmProperties.isEnabled()) {
            log.info("OsmCronJob: disabled, skipping");
            return;
        }

        if (!running.compareAndSet(false, true)) {
            log.warn("OsmCronJob: previous run still in progress, skipping");
            return;
        }

        LocalDateTime start = LocalDateTime.now();
        log.info("OsmCronJob: starting at={} maxUrlsPerRun={}", start, osmProperties.getMaxUrlsPerRun());

        try {
            DirectoryCrawlResult result = crawlerService.crawlSource(
                    osmFarmSource,
                    osmProperties.getMaxUrlsPerRun()
            );

            log.info("OsmCronJob: finished. fetched={}, skippedDuplicate={}, processed={}, ok={}, errors={}, durationMs={}",
                    result.urlsFetched(),
                    result.urlsSkippedDuplicate(),
                    result.urlsProcessed(),
                    result.urlsScrapedOk(),
                    result.urlsScrapedError(),
                    result.durationMs());

        } catch (Exception e) {
            log.error("OsmCronJob: failed: {}", e.getMessage(), e);
        } finally {
            running.set(false);
        }
    }
}