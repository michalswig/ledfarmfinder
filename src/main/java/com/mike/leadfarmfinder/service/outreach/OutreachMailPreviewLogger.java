package com.mike.leadfarmfinder.service.outreach;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OutreachMailPreviewLogger {

    public void logPreview(PreparedMail mail) {
        if (mail == null) return;
        if (!log.isDebugEnabled()) return;

        log.debug("=== OUTREACH EMAIL PREVIEW ===");
        log.debug("From: {}", mail.from());
        log.debug("To: {}", mail.to());
        log.debug("Subject: {}", mail.subject());
        log.debug("Body:\n{}", mail.body());
        log.debug("=== END OUTREACH EMAIL PREVIEW ===");
    }
}