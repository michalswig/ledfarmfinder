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
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutreachService {

    private static final Pattern EMAIL_REGEX =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final OutreachProperties outreachProperties;
    private final FarmLeadRepository farmLeadRepository;
    private final JavaMailSender mailSender;
    private final OutreachMailPreviewLogger previewLogger;
    private final MailSenderGateway mailSenderGateway;
    private final UnsubscribeUrlBuilder unsubscribeUrlBuilder;

    public void sendFirstEmail(FarmLead lead) {
        if (!isOutreachEnabledOrLog()) return;
        if (lead == null) {
            log.warn("OutreachService: lead is null, skipping");
            return;
        }
        if (!isEligibleLeadOrLog(lead)) return;

        String to = normalizeAndValidateOrDeactivate(lead);
        if (to == null) return;

        String from = outreachProperties.getFromAddress();
        String subject = outreachProperties.getDefaultSubject();

        String unsubscribeUrl = unsubscribeUrlBuilder.build(lead);
        Map<String, String> vars = buildTemplateVars(to, unsubscribeUrl);

        String template = outreachProperties.getFirstEmailBodyTemplate();
        String body = renderTemplate(template, vars);

        previewLogger.logPreview(new PreparedMail(from, to, subject, body, unsubscribeUrl));

        SendResult result = sendOrSimulate(from, to, subject, body, unsubscribeUrl);

        if (result.hardBounce()) {
            markHardBounce(lead);
            return;
        }

        if (!result.sent() && !outreachProperties.isSimulateOnly()) {
            log.info("OutreachService: not sent (no simulate, no hardBounce) -> no timestamps update for {}", to);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        applyFirstEmailTimestamps(lead, now);

        // opcjonalnie: zapis normalizacji email
        lead.setEmail(to);

        farmLeadRepository.save(lead);
    }

    public void sendFollowUpEmail(FarmLead lead) {
        if (!isOutreachEnabledOrLog()) return;
        if (lead == null) {
            log.warn("OutreachService: lead is null, skipping");
            return;
        }
        if (!isEligibleLeadOrLog(lead)) return;

        if (!hasFirstEmailOrLog(lead)) return;

        LocalDateTime now = LocalDateTime.now();
        if (isLastEmailTooRecent(lead, now)) return;

        String to = normalizeAndValidateOrDeactivate(lead);
        if (to == null) return;

        String from = outreachProperties.getFromAddress();
        String subject = outreachProperties.getFollowUpSubject();
        String template = outreachProperties.getFollowUpEmailBodyTemplate();

        String unsubscribeUrl = unsubscribeUrlBuilder.build(lead);
        Map<String, String> vars = buildTemplateVars(to, unsubscribeUrl);
        String body = renderTemplate(template, vars);

        previewLogger.logPreview(new PreparedMail(from, to, subject, body, unsubscribeUrl));

        SendResult result = sendOrSimulate(from, to, subject, body, unsubscribeUrl);

        if (result.hardBounce()) {
            markHardBounce(lead);
            return;
        }

        if (!result.sent() && !outreachProperties.isSimulateOnly()) {
            log.info("OutreachService: follow-up not sent -> no timestamps update for {}", to);
            return;
        }

        lead.setLastEmailSentAt(now);
        lead.setEmail(to);
        farmLeadRepository.save(lead);
    }

    private boolean hasFirstEmailOrLog(FarmLead lead) {
        if (lead.getFirstEmailSentAt() != null) return true;

        log.info("OutreachService: follow-up skipped, firstEmailSentAt is null (id={}, email={})",
                lead.getId(), lead.getEmail());
        return false;
    }

    private boolean isLastEmailTooRecent(FarmLead lead, LocalDateTime now) {
        LocalDateTime last = lead.getLastEmailSentAt();
        if (last == null) return false;

        int minDays = outreachProperties.getFollowUpMinDaysSinceLastEmail();
        LocalDateTime cutoff = now.minusDays(minDays);

        if (last.isAfter(cutoff)) {
            log.info("OutreachService: follow-up skipped, lastEmailSentAt={} is younger than {} days (id={}, email={})",
                    last, minDays, lead.getId(), lead.getEmail());
            return true;
        }

        return false;
    }


    // =========================
    // WSPÓLNE KROKI (pod follow-up)
    // =========================

    private boolean isOutreachEnabledOrLog() {
        if (!outreachProperties.isEnabled()) {
            log.info("OutreachService: outreach disabled, skipping");
            return false;
        }
        return true;
    }

    private boolean isEligibleLeadOrLog(FarmLead lead) {
        if (!lead.isActive()) {
            log.info("OutreachService: lead inactive, skipping (id={}, email={})", lead.getId(), lead.getEmail());
            return false;
        }
        if (lead.isBounce()) {
            log.info("OutreachService: lead bounce=true, skipping (id={}, email={})", lead.getId(), lead.getEmail());
            return false;
        }
        return true;
    }

    /**
     * Zwraca znormalizowany email jeśli OK.
     * Jeśli email pusty/niepoprawny -> deaktywuje lead i zwraca null.
     */
    private String normalizeAndValidateOrDeactivate(FarmLead lead) {
        String to = normalizeEmail(lead.getEmail());
        if (to.isBlank()) {
            log.warn("OutreachService: empty email after normalize, deactivating lead (id={})", lead.getId());
            deactivateAsInvalid(lead, "EMPTY_EMAIL");
            return null;
        }

        // blokada na śmieciowe/sklejone adresy
        if (!isValidEmail(to)) {
            log.warn("OutreachService: invalid email format, deactivating lead (id={}, rawEmail={}, normalized={})",
                    lead.getId(), lead.getEmail(), to);
            deactivateAsInvalid(lead, "INVALID_EMAIL_FORMAT");
            return null;
        }

        // tymczasowa blokada Telekom (T-Online/T-Mobile) – 550 5.7.0 IP reputation
        if (isBlockedProviderDomain(to)) {
            log.info("OutreachService: skipping blocked provider domain (Telekom) for now: {}", to);
            return null;
        }

        return to;
    }

    private static final String[] TELEKOM_DOMAINS = {
            "@t-online.de",
            "@t-mobile.de",
            "@magenta.de",
            "@telekom.de"
            // opcjonalnie: "@telekom.de"
    };

    private boolean isBlockedProviderDomain(String email) {
        if (email == null) return false;
        String e = email.toLowerCase();
        for (String d : TELEKOM_DOMAINS) {
            if (e.endsWith(d)) return true;
        }
        return false;
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

    // =========================
    // ISTNIEJĄCE METODY (bez zmian logiki)
    // =========================

    private void deactivateAsInvalid(FarmLead lead, String reason) {
        lead.setActive(false);
        lead.setBounce(true); // traktuj jak “nieużywalne”
        farmLeadRepository.save(lead);
        log.info("OutreachService: lead deactivated as invalid (id={}, reason={})", lead.getId(), reason);
    }

    private boolean isValidEmail(String email) {
        return EMAIL_REGEX.matcher(email).matches();
    }

    private String normalizeEmail(String email) {
        if (email == null) return "";

        String e = email.trim();

        // "email@domena.dewww.domena.de" -> utnij od www
        int at = e.indexOf('@');
        int www = e.indexOf("www.");
        if (at > 0 && www > at) {
            e = e.substring(0, www);
        }

        // usuń przecinki/średniki na końcu
        e = e.replaceAll("[,;]+$", "");

        return e.trim().toLowerCase();
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
