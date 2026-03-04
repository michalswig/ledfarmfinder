package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.config.OutreachProperties;
import com.mike.leadfarmfinder.entity.FarmLead;
import com.mike.leadfarmfinder.repository.FarmLeadRepository;
import com.mike.leadfarmfinder.service.outreach.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutreachService {

    private final OutreachProperties outreachProperties;
    private final FarmLeadRepository farmLeadRepository;
    private final JavaMailSender mailSender;
    private final OutreachMailPreviewLogger previewLogger;
    private final MailSenderGateway mailSenderGateway;
    private final UnsubscribeUrlBuilder unsubscribeUrlBuilder;
    private final LeadEmailNormalizer leadEmailNormalizer;
    private final LeadEligibilityPolicy leadEligibilityPolicy;

    public void sendFirstEmail(FarmLead lead) {
        send(lead, EmailType.FIRST);
    }

    public void sendFollowUpEmail(FarmLead lead) {
        send(lead, EmailType.FOLLOW_UP);
    }

    private void send(FarmLead lead, EmailType type) {
        if (!isOutreachEnabledOrLog()) return;

        LocalDateTime now = LocalDateTime.now();

        if (!leadEligibilityPolicy.isEligibleOrLog(lead, type, LocalDateTime.now())) return;

        String to = leadEmailNormalizer.normalizeAndValidateOrDeactivate(lead);
        if (to == null) return;

        String from = outreachProperties.getFromAddress();

        String unsubscribeUrl = unsubscribeUrlBuilder.build(lead);
        Map<String, String> vars = buildTemplateVars(to, unsubscribeUrl);

        String subject;
        String template;

        if (type == EmailType.FIRST) {
            subject = outreachProperties.getDefaultSubject();
            template = outreachProperties.getFirstEmailBodyTemplate();
        } else {
            subject = outreachProperties.getFollowUpSubject();
            template = outreachProperties.getFollowUpEmailBodyTemplate();
        }

        String body = renderTemplate(template, vars);

        PreparedMail mail = new PreparedMail(from, to, subject, body, unsubscribeUrl);
        previewLogger.logPreview(mail);

        SendResult result = sendOrSimulate(from, to, subject, body, unsubscribeUrl);

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

    private Map<String, String> buildTemplateVars(String email, String unsubscribeUrl) {
        Map<String, String> vars = new HashMap<>();
        vars.put("EMAIL", email);
        vars.put("UNSUBSCRIBE_URL", unsubscribeUrl);
        return vars;
    }

    private SendResult sendOrSimulate(String from, String to, String subject, String body, String unsubscribeUrl) {
        if (outreachProperties.isSimulateOnly()) {
            log.info("OutreachService: simulate-only=true, skipping real SMTP send");
            return new SendResult(true, false);
        }
        PreparedMail mail = new PreparedMail(from, to, subject, body, unsubscribeUrl);
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

    private String renderTemplate(String template, Map<String, String> variables) {
        if (template == null) return "";
        String result = template;
        for (var entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }
}