package com.mike.leadfarmfinder.service.outreach;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OutreachMailPreviewLogger {

    public void logPreview(PreparedMail mail) {
        if (mail == null) return;

        log.info("=== OUTREACH EMAIL PREVIEW ===");
        log.info("From: {}", mail.from());
        log.info("To: {}", mail.to());
        log.info("Subject: {}", mail.subject());
        log.info("Body:\n{}", mail.body());
        log.info("=== END OUTREACH EMAIL PREVIEW ===");
    }
}
