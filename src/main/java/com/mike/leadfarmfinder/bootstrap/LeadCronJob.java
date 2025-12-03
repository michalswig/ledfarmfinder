package com.mike.leadfarmfinder.bootstrap;

import com.mike.leadfarmfinder.service.DiscoveryService;
import com.mike.leadfarmfinder.service.FarmScraperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class LeadCronJob {

    private final DiscoveryService discoveryService;
    private final FarmScraperService farmScraperService;

    @Value("${leadcron.enabled:true}")
    private boolean cronEnabled;

    @Value("${leadcron.max-urls-per-run:100}")
    private int maxUrlsPerRun;

    @Scheduled(fixedDelayString = "${leadcron.interval-millis:600000}")
    public void runLeadDiscoveryJob() {

        if (!cronEnabled) {
            log.info("LeadCronJob: disabled via property leadcron.enabled=false");
            return;
        }

        LocalDateTime start = LocalDateTime.now();
        log.info("LeadCronJob: started at {}", start);

        List<String> candidateFarmUrls;
        try {
            candidateFarmUrls = discoveryService.findCandidateFarmUrls(maxUrlsPerRun);
        } catch (Exception e) {
            log.warn("LeadCronJob: discoveryService.findCandidateFarmUrls failed: {}", e.getMessage(), e);
            return;
        }

        if (candidateFarmUrls.isEmpty()) {
            log.info("LeadCronJob: no candidate farm URLs found (maxUrlsPerRun={})", maxUrlsPerRun);
            return;
        }

        log.info("LeadCronJob: {} candidate URLs returned (limit={})",
                candidateFarmUrls.size(), maxUrlsPerRun);

        int successCount = 0;
        int errorCount = 0;

        for (String url : candidateFarmUrls) {
            try {
                log.debug("LeadCronJob: scraping URL={}", url);
                farmScraperService.scrapeFarmLeads(url);
                successCount++;
            } catch (Exception e) {
                errorCount++;
                log.warn("LeadCronJob: error while scraping url={}: {}",
                        url, e.getMessage());
            }
        }

        log.info("LeadCronJob: finished run at {}, scraped OK={}, errors={}",
                LocalDateTime.now(), successCount, errorCount);
    }
}
