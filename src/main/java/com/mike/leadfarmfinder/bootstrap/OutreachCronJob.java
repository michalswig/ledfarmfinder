package com.mike.leadfarmfinder.bootstrap;

import com.mike.leadfarmfinder.config.OutreachProperties;
import com.mike.leadfarmfinder.entity.FarmLead;
import com.mike.leadfarmfinder.repository.FarmLeadRepository;
import com.mike.leadfarmfinder.service.OutreachService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutreachCronJob {

    private final FarmLeadRepository farmLeadRepository;
    private final OutreachService outreachService;
    private final OutreachProperties outreachProperties;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${leadfinder.outreach.interval-millis:900000}")
    public void runOutreachBatch() {
        if (!outreachProperties.isEnabled()) {
            log.debug("OutreachCronJob: outreach disabled, skipping");
            return;
        }

        if (!running.compareAndSet(false, true)) {
            log.debug("OutreachCronJob: already running, skipping");
            return;
        }

        try {
            int batchSize = outreachProperties.getMaxEmailsPerRun();
            if (batchSize <= 0) {
                log.warn("OutreachCronJob: maxEmailsPerRun <= 0, nothing to do");
                return;
            }

            Pageable pageable = PageRequest.of(0, batchSize);
            List<FarmLead> leads = farmLeadRepository
                    .findByActiveTrueAndBounceFalseAndFirstEmailSentAtIsNullOrderByCreatedAtAsc(pageable);

            if (leads.isEmpty()) {
                log.debug("OutreachCronJob: no new leads to email");
                return;
            }

            LocalDateTime start = LocalDateTime.now();
            log.info("OutreachCronJob: started. leads={} batchSize={}", leads.size(), batchSize);

            int successCount = 0;
            int errorCount = 0;
            long delayMs = outreachProperties.getDelayBetweenEmailsMillis();

            for (FarmLead lead : leads) {
                try {
                    outreachService.sendFirstEmail(lead);
                    successCount++;

                    if (delayMs > 0 && successCount < leads.size()) {
                        Thread.sleep(delayMs);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("OutreachCronJob: interrupted during delay");
                    break;
                } catch (Exception e) {
                    errorCount++;
                    log.warn("OutreachCronJob: error sending to leadId={} msg={}", lead.getId(), e.getMessage());
                }
            }

            log.info("OutreachCronJob: finished. sent={} errors={} duration={}ms",
                    successCount, errorCount, java.time.Duration.between(start, LocalDateTime.now()).toMillis());

        } finally {
            running.set(false);
        }
    }
}