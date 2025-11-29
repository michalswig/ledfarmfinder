package com.mike.leadfarmfinder.bootstrap;

import com.mike.leadfarmfinder.service.DiscoveryService;
import com.mike.leadfarmfinder.service.FarmScraperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class LeadCronJob {
    private final DiscoveryService discoveryService;
    private final FarmScraperService farmScraperService;

    @Scheduled(fixedRate = 300_000)
    public void runHourlyCronJob() {

        log.info("LeadCronJob: started at {}", LocalDateTime.now());

        List<String> candidateFarmUrls = discoveryService.findCandidateFarmUrls(100);

        log.info("LeadCronJob: {} candidate URLs", candidateFarmUrls.size());

        if (candidateFarmUrls.isEmpty()) {
            log.info("No candidate farm urls found");
            return;
        }
        candidateFarmUrls.forEach(farmScraperService::scrapeFarmLeads);
    }

}
