package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.config.OutreachProperties;
import com.mike.leadfarmfinder.entity.FarmLead;
import com.mike.leadfarmfinder.repository.FarmLeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutreachService {

    private final OutreachProperties outreachProperties;
    private final FarmLeadRepository farmLeadRepository;

    public void sendFirstEmail(FarmLead lead) {
        if (!outreachProperties.isEnabled()) {
            log.info("OutreachService: outreach disabled, skipping (leadId={}, email={})",
                    lead != null ? lead.getId() : null,
                    lead != null ? lead.getEmail() : null
            );
            return;
        }

        if (lead == null) {
            log.warn("OutreachService: lead is null, skipping");
            return;
        }

        if (!lead.isActive()) {
            log.info("OutreachService: lead {} is inactive, skipping", lead.getEmail());
            return;
        }

        if (lead.isBounce()) {
            log.info("OutreachService: lead {} is marked as bounce, skipping", lead.getEmail());
            return;
        }

        String from = outreachProperties.getFromAddress();
        String to = lead.getEmail();
        String subject = outreachProperties.getDefaultSubject();
        String body = buildEmailBody();

        // NA RAZIE: tylko log
        log.info("=== OUTREACH EMAIL PREVIEW ===");
        log.info("From: {}", from);
        log.info("To: {}", to);
        log.info("Subject: {}", subject);
        log.info("Body:\n{}", body);
        log.info("=== END OUTREACH EMAIL PREVIEW ===");

        // >>> LF-5.2: aktualizacja pól w leadzie (symulujemy „wysłano”)
        LocalDateTime now = LocalDateTime.now();

        if (lead.getFirstEmailSentAt() == null) {
            lead.setFirstEmailSentAt(now);
        }
        lead.setLastEmailSentAt(now);

        farmLeadRepository.save(lead);
    }

    private String buildEmailBody() {
        return """
                Guten Tag,

                wir unterstützen landwirtschaftliche Betriebe in Deutschland
                bei der Gewinnung von zuverlässigen Saisonarbeitskräften aus Polen.

                Wenn Sie in der kommenden Saison zusätzliche Hände brauchen,
                können wir Sie gerne unterstützen.

                Mit freundlichen Grüßen
                LeadFarmFinder
                """;
    }
}
