package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.config.OutreachProperties;
import com.mike.leadfarmfinder.entity.FarmLead;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutreachService {

    private final OutreachProperties outreachProperties;

    // TODO LF-5.6: wstrzykniemy tu jeszcze unsubscribeBaseUrl i dodamy link

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

        String from = outreachProperties.getFromAddress();
        String to = lead.getEmail();
        String subject = outreachProperties.getDefaultSubject();

        String body = buildEmailBody(); // na razie bez unsubscribe linka

        log.info("=== OUTREACH EMAIL PREVIEW ===");
        log.info("From: {}", from);
        log.info("To: {}", to);
        log.info("Subject: {}", subject);
        log.info("Body:\n{}", body);
        log.info("=== END OUTREACH EMAIL PREVIEW ===");
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
