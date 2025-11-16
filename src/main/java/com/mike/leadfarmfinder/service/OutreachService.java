package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.entity.FarmLead;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutreachService {

    @Value("${app.outreach.unsubscribe-base-url}")
    private String unsubscribeBaseurl;
    @Value("${app.outreach.from}")
    private String fromAddress;
    // docelowo wstrzykniemy JavaMailSender, na razie tylko logi

    public void sendInitialEmail(FarmLead lead) {
        if (lead == null) {
            log.warn("OutreachService: lead is null, skipping");
            return;
        }

        if (!lead.isActive()) {
            log.info("OutreachService: lead {} is inactive, skipping", lead.getEmail());
            return;
        }

        if (lead.getUnsubscribeToken() == null) {
            log.warn("OutreachService: lead {} has no unsubscribe token, skipping", lead.getEmail());
            return;
        }

        String unsubscribeLink = unsubscribeBaseurl + lead.getUnsubscribeToken();
        String body = buildEmailBody(unsubscribeLink);

        // NA RAZIE TYLKO LOGUJEMY:
        log.info("=== OUTREACH EMAIL PREVIEW ===");
        log.info("From: {}", fromAddress);
        log.info("To: {}", lead.getEmail());
        log.info("Subject: {}", "Seasonal workers for your farm");
        log.info("Body:\n{}", body);
        log.info("=== END OUTREACH EMAIL PREVIEW ===");
    }

    private String buildEmailBody(String unsubscribeLink) {
        return """
                Guten Tag,

                wir unterstützen landwirtschaftliche Betriebe in Deutschland bei der Gewinnung von zuverlässigen Saisonarbeitskräften aus Polen.

                Wenn Sie in der kommenden Saison zusätzliche Hände brauchen, können wir Sie gerne unterstützen.

                Wenn Sie kein Interesse haben, klicken Sie bitte auf den folgenden Link, um keine weiteren Nachrichten zu erhalten:
                %s

                Mit freundlichen Grüßen
                LeadFarmFinder
                """.formatted(unsubscribeLink);
    }
}
