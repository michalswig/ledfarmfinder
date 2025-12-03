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

@Slf4j
@Service
@RequiredArgsConstructor
public class OutreachService {

    private final OutreachProperties outreachProperties;
    private final FarmLeadRepository farmLeadRepository;
    private final JavaMailSender mailSender;

    @Value("${app.outreach.unsubscribe-base-url:}")
    private String unsubscribeBaseUrl;

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

        // 🔁 template + placeholdery
        Map<String, String> vars = new HashMap<>();
        vars.put("EMAIL", lead.getEmail());

        String unsubscribeUrl = "";
        if (unsubscribeBaseUrl != null && !unsubscribeBaseUrl.isBlank()
                && lead.getUnsubscribeToken() != null && !lead.getUnsubscribeToken().isBlank()) {
            unsubscribeUrl = unsubscribeBaseUrl + lead.getUnsubscribeToken();
        }
        vars.put("UNSUBSCRIBE_URL", unsubscribeUrl);

        String template = outreachProperties.getFirstEmailBodyTemplate();
        String body = renderTemplate(template, vars);

        // 🧪 preview
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
            sent = true; // logicznie traktujemy jako "poszło" na potrzeby timestampów
        } else {
            try {
                // ⬇️ tu dodaliśmy unsubscribeUrl
                sendViaSmtp(from, to, subject, body, unsubscribeUrl);
                log.info("OutreachService: email sent successfully to {}", to);
                sent = true;
            } catch (MailException e) {
                log.warn("OutreachService: FAILED to send email to {} (MailException): {}", to, e.getMessage());

                if (isHardBounce(e)) {
                    hardBounce = true;
                    log.info("OutreachService: detected HARD BOUNCE (5xx) for {}", to);
                } else {
                    log.info("OutreachService: send failed for {}, but no hard-bounce (5xx) detected – leaving lead active", to);
                }

            } catch (Exception e) {
                log.warn("OutreachService: FAILED to send email to {} (Exception): {}", to, e.getMessage());
                // traktujemy jako problem techniczny po naszej stronie – bez bounce
            }
        }

        // 🚫 Jeśli hard bounce (5xx) – oznaczamy lead i kończymy
        if (hardBounce) {
            lead.setBounce(true);
            lead.setActive(false);
            farmLeadRepository.save(lead);
            return;
        }

        // Jeśli nie wysłano (sent=false) i to nie była tylko symulacja → nie ruszamy timestampów
        if (!sent && !outreachProperties.isSimulateOnly()) {
            log.info("OutreachService: email not sent to {} (no simulate, no hardBounce) – no timestamps update", to);
            return;
        }

        // ✅ Aktualizacja timestampów tylko gdy "wysłane" (realnie lub symulacja)
        LocalDateTime now = LocalDateTime.now();
        if (lead.getFirstEmailSentAt() == null) {
            lead.setFirstEmailSentAt(now);
        }
        lead.setLastEmailSentAt(now);

        farmLeadRepository.save(lead);
    }

    // ⬇️ Zmienione: dodany parametr unsubscribeUrl i nagłówki List-Unsubscribe
    private void sendViaSmtp(String from, String to, String subject, String body, String unsubscribeUrl) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();

        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(body, false); // false = plain text

        // 🔥 List-Unsubscribe — tylko jeśli mamy sensowny URL
        if (unsubscribeUrl != null && !unsubscribeUrl.isBlank()) {
            String headerValue = "<" + unsubscribeUrl + ">";
            message.addHeader("List-Unsubscribe", headerValue);
            // Gmail One-Click (opcjonalne, ale warto)
            message.addHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        }

        mailSender.send(message);
    }

    private boolean isHardBounce(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof SMTPAddressFailedException smtpEx) {
                int code = smtpEx.getReturnCode();
                if (code >= 500 && code < 600) {
                    log.debug("Detected SMTP hard bounce code {} for address {}", code, smtpEx.getAddress());
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    private String renderTemplate(String template, Map<String, String> variables) {
        if (template == null) {
            return "";
        }
        String result = template;
        for (var entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }
}
