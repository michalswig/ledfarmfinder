package com.mike.leadfarmfinder.service.outreach;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "leadfinder.email.provider",
        havingValue = "smtp",
        matchIfMissing = true
)
public class SmtpMailSenderGateway implements MailSenderGateway {

    private final JavaMailSender mailSender;

    @Override
    public SendResult send(PreparedMail mail) {
        boolean hardBounce = false;
        boolean sent = false;

        try {
            sendViaSmtp(mail);
            log.info("Outreach: email sent successfully to {}", mail.to());
            sent = true;
        } catch (MailException e) {
            log.warn("Outreach: FAILED to send email to {} (MailException): {}", mail.to(), e.getMessage());

            if (isHardBounce(e)) {
                hardBounce = true;
                log.info("Outreach: detected HARD BOUNCE (5xx) for {}", mail.to());
            } else {
                log.info("Outreach: send failed, but no hard-bounce detected; leaving lead active (email={})", mail.to());
            }
        } catch (Exception e) {
            log.warn("Outreach: FAILED to send email to {} (Exception): {}", mail.to(), e.getMessage(), e);
        }

        return new SendResult(sent, hardBounce);
    }

    private void sendViaSmtp(PreparedMail mail) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();

        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(mail.from());
        helper.setTo(mail.to());
        helper.setSubject(mail.subject());
        helper.setText(mail.body(), false);

        String unsubscribeUrl = mail.unsubscribeUrl();

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

}
