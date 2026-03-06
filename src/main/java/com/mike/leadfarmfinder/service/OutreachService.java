package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.config.OutreachProperties;
import com.mike.leadfarmfinder.entity.FarmLead;
import com.mike.leadfarmfinder.repository.FarmLeadRepository;
import com.mike.leadfarmfinder.service.outreach.*;
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
    private final OutreachMailPreviewLogger previewLogger;
    private final MailSenderGateway mailSenderGateway;
    private final LeadEmailNormalizer leadEmailNormalizer;
    private final LeadEligibilityPolicy leadEligibilityPolicy;
    private final MailComposer mailComposer;

    public void sendFirstEmail(FarmLead lead) {
        send(lead, EmailType.FIRST);
    }

    public void sendFollowUpEmail(FarmLead lead) {
        send(lead, EmailType.FOLLOW_UP);
    }

    private void send(FarmLead lead, EmailType type) {
        if (!isOutreachEnabledOrLog()) return;

        LocalDateTime now = LocalDateTime.now();

        if (!leadEligibilityPolicy.isEligibleOrLog(lead, type, now)) return;

        String to = leadEmailNormalizer.normalizeAndValidateOrDeactivate(lead);
        if (to == null) return;

        PreparedMail mail = mailComposer.compose(lead, to, type);
        previewLogger.logPreview(mail);

        SendResult result = sendOrSimulate(mail);

        if (result.hardBounce()) {
            markHardBounce(lead);
            return;
        }

        if (!result.sent() && !outreachProperties.isSimulateOnly()) {
            log.info("OutreachService: not sent (no simulate, no hardBounce) -> no timestamps update for {}", to);
            return;
        }

        if (type == EmailType.FIRST) {
            applyFirstEmailTimestamps(lead, now);
        } else {
            lead.setLastEmailSentAt(now);
        }

        lead.setEmail(to);
        farmLeadRepository.save(lead);
    }

    private boolean isOutreachEnabledOrLog() {
        if (!outreachProperties.isEnabled()) {
            log.info("OutreachService: outreach disabled, skipping");
            return false;
        }
        return true;
    }

    private SendResult sendOrSimulate(PreparedMail mail) {
        if (outreachProperties.isSimulateOnly()) {
            log.info("OutreachService: simulate-only=true, skipping real SMTP send");
            return new SendResult(true, false);
        }
        return mailSenderGateway.send(mail);
    }

    private void markHardBounce(FarmLead lead) {
        lead.setBounce(true);
        lead.setActive(false);
        farmLeadRepository.save(lead);
    }

    private void applyFirstEmailTimestamps(FarmLead lead, LocalDateTime now) {
        if (lead.getFirstEmailSentAt() == null) {
            lead.setFirstEmailSentAt(now);
        }
        lead.setLastEmailSentAt(now);
    }
}