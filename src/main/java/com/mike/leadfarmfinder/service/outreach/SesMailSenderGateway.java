package com.mike.leadfarmfinder.service.outreach;

import com.mike.leadfarmfinder.config.AwsSesProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.MessageHeader;
import software.amazon.awssdk.services.sesv2.model.MessageTag;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "leadfinder.email.provider", havingValue = "ses")
public class SesMailSenderGateway implements MailSenderGateway {

    private final SesV2Client sesV2Client;
    private final AwsSesProperties awsSesProperties;

    @Override
    public SendResult send(PreparedMail mail) {
        try {
            SendEmailRequest request = buildRequest(mail);
            var response = sesV2Client.sendEmail(request);

            log.info("Outreach: email accepted by SES for {}, providerMessageId={}",
                    mail.to(), response.messageId());

            return new SendResult(true, false, response.messageId());

        } catch (SesV2Exception e) {
            String errorMessage = e.awsErrorDetails() != null
                    ? e.awsErrorDetails().errorMessage()
                    : e.getMessage();

            String errorCode = e.awsErrorDetails() != null
                    ? e.awsErrorDetails().errorCode()
                    : "UNKNOWN";

            log.warn("Outreach: SES rejected email to {}: code={}, message={}",
                    mail.to(), errorCode, errorMessage);

            return new SendResult(false, false);
        } catch (Exception e) {
            log.warn("Outreach: FAILED to send email to {}: {}", mail.to(), e.getMessage(), e);
            return new SendResult(false, false);
        }
    }

    private SendEmailRequest buildRequest(PreparedMail mail) {
        Message.Builder messageBuilder = Message.builder()
                .subject(c -> c
                        .data(mail.subject())
                        .charset("UTF-8"))
                .body(b -> b
                        .text(c -> c
                                .data(mail.body())
                                .charset("UTF-8")));

        if (mail.unsubscribeUrl() != null && !mail.unsubscribeUrl().isBlank()) {
            messageBuilder.headers(
                    MessageHeader.builder()
                            .name("List-Unsubscribe")
                            .value("<" + mail.unsubscribeUrl() + ">")
                            .build(),
                    MessageHeader.builder()
                            .name("List-Unsubscribe-Post")
                            .value("List-Unsubscribe=One-Click")
                            .build()
            );
        }

        List<MessageTag> tags = new ArrayList<>();

        if (mail.leadId() != null) {
            tags.add(MessageTag.builder()
                    .name("leadId")
                    .value(String.valueOf(mail.leadId()))
                    .build());
        }

        if (mail.emailType() != null && !mail.emailType().isBlank()) {
            tags.add(MessageTag.builder()
                    .name("emailType")
                    .value(mail.emailType())
                    .build());
        }

        SendEmailRequest.Builder builder = SendEmailRequest.builder()
                .fromEmailAddress(mail.from())
                .destination(d -> d.toAddresses(mail.to()))
                .content(c -> c.simple(messageBuilder.build()));

        if (awsSesProperties.configurationSet() != null
                && !awsSesProperties.configurationSet().isBlank()) {
            builder.configurationSetName(awsSesProperties.configurationSet());
        }

        if (!tags.isEmpty()) {
            builder.emailTags(tags);
        }

        return builder.build();
    }
}