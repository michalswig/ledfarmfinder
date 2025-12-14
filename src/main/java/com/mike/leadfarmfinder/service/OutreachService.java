package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.config.OutreachProperties;
import com.mike.leadfarmfinder.entity.FarmLead;
import com.mike.leadfarmfinder.repository.FarmLeadRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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

    @Value("${app.outreach.unsubscribe-base-url:}")
    private String unsubscribeBaseUrl;

    public void sendFirstEmail(FarmLead lead) {
        if (!outreachProperties.isEnabled()) {
            log.info("OutreachService: outreach disabled, skipping");
            return;
        }
        if (lead == null) {
            log.warn("OutreachService: lead is null, skipping");
            return;
        }
        if (!lead.isActive()) {
            log.info("OutreachService: lead inactive, skipping (id={}, email={})", lead.getId(), lead.getEmail());
            return;
        }
        if (lead.isBounce()) {
            log.info("OutreachService: lead bounce=true, skipping (id={}, email={})", lead.getId(), lead.getEmail());
            return;
        }

        String to = normalizeEmail(lead.getEmail());
        if (to.isBlank()) {
            log.warn("OutreachService: empty email after normalize, deactivating lead (id={})", lead.getId());
            deactivateAsInvalid(lead, "EMPTY_EMAIL");
            return;
        }

        // 🔥 KRYTYCZNE: blokada na śmieciowe/sklejone adresy
        if (!isValidEmail(to)) {
            log.warn("OutreachService: invalid email format, deactivating lead (id={}, rawEmail={}, normalized={})",
                    lead.getId(), lead.getEmail(), to);
            deactivateAsInvalid(lead, "INVALID_EMAIL_FORMAT");
            return;
        }

        String from = outreachProperties.getFromAddress();
        String subject = outreachProperties.getDefaultSubject();

        String unsubscribeUrl = buildUnsubscribeUrl(lead);

        Map<String, String> vars = new HashMap<>();
        vars.put("EMAIL", to);
        vars.put("UNSUBSCRIBE_URL", unsubscribeUrl);

        String template = outreachProperties.getFirstEmailBodyTemplate();
        String body = renderTemplate(template, vars);

        // Preview (zostawione, ale bezpieczniej logować krócej w przyszłości)
        log.info("=== OUTREACH EMAIL PREVIEW ===");
        log.info("From: {}", from);
        log.info("To: {}", to);
        log.info("Subject: {}", subject);
        log.info("Body:\n{}", body);
        log.info("=== END OUTREACH EMAIL PREVIEW ===");

        boolean hardBounce = false;
        boolean sent = false;

        if (outreachProperties.isSimulateOnly()) {
            log.info("OutreachService: simulate-only=true, skipping real SMTP send");
            sent = true;
        } else {
            try {
                sendViaSmtp(from, to, subject, body, unsubscribeUrl);
                log.info("OutreachService: email sent successfully to {}", to);
                sent = true;
            } catch (MailException e) {
                log.warn("OutreachService: FAILED to send email to {} (MailException): {}", to, e.getMessage());

                if (isHardBounce(e)) {
                    hardBounce = true;
                    log.info("OutreachService: detected HARD BOUNCE (5xx) for {}", to);
                } else {
                    log.info("OutreachService: send failed, but no hard-bounce detected; leaving lead active (id={}, email={})",
                            lead.getId(), to);
                }
            } catch (Exception e) {
                log.warn("OutreachService: FAILED to send email to {} (Exception): {}", to, e.getMessage(), e);
            }
        }

        if (hardBounce) {
            lead.setBounce(true);
            lead.setActive(false);
            farmLeadRepository.save(lead);
            return;
        }

        if (!sent && !outreachProperties.isSimulateOnly()) {
            log.info("OutreachService: not sent (no simulate, no hardBounce) -> no timestamps update for {}", to);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (lead.getFirstEmailSentAt() == null) {
            lead.setFirstEmailSentAt(now);
        }
        lead.setLastEmailSentAt(now);

        // zapisz też znormalizowany email (opcjonalnie, ale pomaga)
        lead.setEmail(to);

        farmLeadRepository.save(lead);
    }

    private void deactivateAsInvalid(FarmLead lead, String reason) {
        // minimalny "quarantine"
        lead.setActive(false);
        lead.setBounce(true); // traktuj jak “nieużywalne”
        farmLeadRepository.save(lead);
        log.info("OutreachService: lead deactivated as invalid (id={}, reason={})", lead.getId(), reason);
    }

    private String buildUnsubscribeUrl(FarmLead lead) {
        if (unsubscribeBaseUrl == null || unsubscribeBaseUrl.isBlank()) return "";
        if (lead.getUnsubscribeToken() == null || lead.getUnsubscribeToken().isBlank()) return "";
        return unsubscribeBaseUrl + lead.getUnsubscribeToken();
    }

    private void sendViaSmtp(String from, String to, String subject, String body, String unsubscribeUrl) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();

        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(body, false);

        if (unsubscribeUrl != null && !unsubscribeUrl.isBlank()) {
            message.addHeader("List-Unsubscribe", "<" + unsubscribeUrl + ">");
            message.addHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        }

        mailSender.send(message);
    }

    private boolean isHardBounce(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof SMTPAddressFailedException smtpEx) {
                int code = smtpEx.getReturnCode();
                return code >= 500 && code < 600;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private boolean isValidEmail(String email) {
        return EMAIL_REGEX.matcher(email).matches();
    }

    private String normalizeEmail(String email) {
        if (email == null) return "";
        // usuń whitespace, niewidzialne znaki, typowe doklejki
        String e = email.trim();

        // bardzo częsty syf: "email@domena.dewww.domena.de"
        // jeśli mamy "@" i potem "www." bez separatora, utnij od www
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
