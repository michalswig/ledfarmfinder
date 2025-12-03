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

@Component
@RequiredArgsConstructor
@Slf4j
public class OutreachCronJob {

    private final FarmLeadRepository farmLeadRepository;
    private final OutreachService outreachService;
    private final OutreachProperties outreachProperties;

    @Scheduled(fixedDelayString = "${leadfinder.outreach.interval-millis:900000}")
    public void runOutreachBatch() {

        if (!outreachProperties.isEnabled()) {
            log.info("OutreachCronJob: outreach disabled (leadfinder.outreach.enabled=false), skipping whole batch");
            return;
        }

        int batchSize = outreachProperties.getMaxEmailsPerRun();
        if (batchSize <= 0) {
            log.warn("OutreachCronJob: maxEmailsPerRun <= 0, nothing to do");
            return;
        }

        LocalDateTime start = LocalDateTime.now();
        log.info("OutreachCronJob: started batch run at {}, batchSize={}", start, batchSize);

        Pageable pageable = PageRequest.of(0, batchSize);

        List<FarmLead> leads;
        try {
            leads = farmLeadRepository
                    .findByActiveTrueAndBounceFalseAndFirstEmailSentAtIsNullOrderByCreatedAtAsc(pageable);
        } catch (Exception e) {
            log.warn("OutreachCronJob: failed to fetch leads from DB: {}", e.getMessage(), e);
            return;
        }

        if (leads.isEmpty()) {
            log.info("OutreachCronJob: no new leads to email (all already contacted)");
            return;
        }

        log.info("OutreachCronJob: processing {} leads in this batch (limit={})",
                leads.size(), batchSize);

        int successCount = 0;
        int errorCount = 0;

        long delayMs = outreachProperties.getDelayBetweenEmailsMillis();

        for (int i = 0; i < leads.size(); i++) {
            FarmLead lead = leads.get(i);

            try {
                outreachService.sendFirstEmail(lead);
                successCount++;
            } catch (Exception e) {
                errorCount++;
                log.warn("OutreachCronJob: error while sending email to leadId={}, email={}: {}",
                        lead.getId(), lead.getEmail(), e.getMessage());
            }

            boolean isLast = (i == leads.size() - 1);
            if (delayMs > 0 && !isLast) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("OutreachCronJob: interrupted during delay between emails, stopping batch");
                    break;
                }
            }
        }

        log.info("OutreachCronJob: finished batch run at {}, processed={}, success={}, errors={}",
                LocalDateTime.now(), leads.size(), successCount, errorCount);
    }
}
