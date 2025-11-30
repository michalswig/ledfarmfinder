package com.mike.leadfarmfinder.bootstrap;

import com.mike.leadfarmfinder.repository.FarmLeadRepository;
import com.mike.leadfarmfinder.service.OutreachService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutreachCronJob {

    private final FarmLeadRepository farmLeadRepository;
    private final OutreachService outreachService;

    @Scheduled(fixedRate = 900_000) // 15 min
    public void runOutreachDryRun() {
        log.info("OutreachCronJob: started dry-run");

        farmLeadRepository
                .findFirstByActiveTrueAndBounceFalseAndFirstEmailSentAtIsNullOrderByCreatedAtAsc()
                .ifPresentOrElse(
                        outreachService::sendFirstEmail,
                        () -> log.info("OutreachCronJob: no new leads to email (all already contacted)")
                );
    }
}

