package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.config.OutreachProperties;
import com.mike.leadfarmfinder.entity.FarmLead;
import com.mike.leadfarmfinder.repository.FarmLeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutreachService {

    private final OutreachProperties outreachProperties;
    private final FarmLeadRepository farmLeadRepository;
    private final JavaMailSender mailSender;   // NEW

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

        // 🧪 1) Zawsze logujemy preview (żeby widzieć co idzie)
        log.info("=== OUTREACH EMAIL PREVIEW ===");
        log.info("From: {}", from);
        log.info("To: {}", to);
        log.info("Subject: {}", subject);
        log.info("Body:\n{}", body);
        log.info("=== END OUTREACH EMAIL PREVIEW ===");

        // 📨 2) Decyzja: wysyłamy naprawdę czy tylko symulacja?
        if (outreachProperties.isSimulateOnly()) {
            log.info("OutreachService: simulate-only=true, skipping real SMTP send");
        } else {
            try {
                sendViaSmtp(from, to, subject, body);
                log.info("OutreachService: email sent successfully to {}", to);
            } catch (Exception e) {
                log.warn("OutreachService: FAILED to send email to {}: {}", to, e.getMessage());
                // UWAGA: tutaj na razie NIE ustawiamy bounce – to będzie osobny krok.
                return; // nie zapisuj firstEmailSentAt, skoro wysyłka się wywaliła
            }
        }

        // ✅ 3) Aktualizacja timestampów TYLKO gdy "wysłane" (realnie lub symulacja)
        LocalDateTime now = LocalDateTime.now();
        if (lead.getFirstEmailSentAt() == null) {
            lead.setFirstEmailSentAt(now);
        }
        lead.setLastEmailSentAt(now);

        farmLeadRepository.save(lead);
    }

    private void sendViaSmtp(String from, String to, String subject, String body) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();

        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(body, false); // false = plain text

        mailSender.send(message);
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
