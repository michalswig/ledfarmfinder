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

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutreachCronJob {

    private final FarmLeadRepository farmLeadRepository;
    private final OutreachService outreachService;
    private final OutreachProperties outreachProperties;

    @Scheduled(fixedRate = 900_000) // 15 minut
    public void runOutreachBatch() {
        log.info("OutreachCronJob: started batch run");

        // 1) globalny kill-switch
        if (!outreachProperties.isEnabled()) {
            log.info("OutreachCronJob: outreach disabled (leadfinder.outreach.enabled=false), skipping whole batch");
            return;
        }

        int batchSize = outreachProperties.getMaxEmailsPerRun();
        if (batchSize <= 0) {
            log.warn("OutreachCronJob: maxEmailsPerRun <= 0, nothing to do");
            return;
        }

        Pageable pageable = PageRequest.of(0, batchSize);

        // 2) pobierz max N świeżych leadów
        List<FarmLead> leads = farmLeadRepository
                .findByActiveTrueAndBounceFalseAndFirstEmailSentAtIsNullOrderByCreatedAtAsc(pageable);

        if (leads.isEmpty()) {
            log.info("OutreachCronJob: no new leads to email (all already contacted)");
            return;
        }

        log.info("OutreachCronJob: processing {} leads in this batch (limit={})",
                leads.size(), batchSize);

        // 3) wyślij (na razie: zaloguj) mail do każdego
        leads.forEach(lead -> {
            try {
                outreachService.sendFirstEmail(lead);
            } catch (Exception e) {
                log.warn("OutreachCronJob: error while sending email to leadId={}, email={}: {}",
                        lead.getId(), lead.getEmail(), e.getMessage());
            }
        });

        log.info("OutreachCronJob: finished batch run, processed {} leads", leads.size());
    }
}
