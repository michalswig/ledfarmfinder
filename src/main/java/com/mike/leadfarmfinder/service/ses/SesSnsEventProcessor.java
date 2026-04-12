package com.mike.leadfarmfinder.service.ses;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mike.leadfarmfinder.service.outreach.event.MailEventMessage;
import com.mike.leadfarmfinder.service.outreach.event.MailEventPublisher;
import com.mike.leadfarmfinder.service.outreach.event.MailEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SesSnsEventProcessor {

    private final ObjectMapper objectMapper;
    private final SesLeadEventService sesLeadEventService;
    private final MailEventPublisher mailEventPublisher;

    public void processSesEvent(String messageJson, String rawPayload) {
        try {
            JsonNode root = objectMapper.readTree(messageJson);

            String eventTypeValue = text(root, "eventType");
            if (eventTypeValue == null) {
                eventTypeValue = text(root, "notificationType");
            }

            JsonNode mail = root.path("mail");
            String sesMessageId = text(mail, "messageId");
            String destination = firstText(mail.path("destination"));
            String leadId = firstTagValue(mail.path("tags"), "leadId");
            String emailType = firstTagValue(mail.path("tags"), "emailType");

            if (eventTypeValue == null || eventTypeValue.isBlank()) {
                log.warn("SES event ignored - missing eventTypeValue/notificationType. sesMessageId={}, leadId={}, destination={}, payload={}",
                        sesMessageId, leadId, destination, messageJson);
                return;
            }

            MailEventType eventType = mapEventType(eventTypeValue);
            if (eventType == null) {
                log.info("Ignoring unsupported SES event type={}, sesMessageId={}, leadId={}, destination={}",
                        eventTypeValue, sesMessageId, leadId, destination);
                return;
            }

            MailEventMessage event = MailEventMessage.builder()
                    .eventType(eventType)
                    .leadId(leadId)
                    .leadEmail(destination)
                    .emailType(emailType)
                    .sesMessageId(sesMessageId)
                    .bounceType(text(root.path("bounce"), "bounceType"))
                    .bounceSubType(text(root.path("bounce"), "bounceSubType"))
                    .diagnosticCode(firstDiagnosticCode(root.path("bounce").path("bouncedRecipients")))
                    .status(null)
                    .action(null)
                    .rawPayload(rawPayload)
                    .occurredAt(LocalDateTime.now())
                    .build();

            log.info("Publishing SES event: type={}, sesMessageId={}, leadId={}, destination={}, emailType={}",
                    eventType, sesMessageId, leadId, destination, emailType);

            mailEventPublisher.publish(event);

        } catch (Exception e) {
            log.error("Failed to process SES event payload: {}", messageJson, e);
        }
    }

    private MailEventType mapEventType(String eventTypeValue) {
        return switch (eventTypeValue){
            case "Bounce" -> MailEventType.BOUNCE;
            case "Complaint" -> MailEventType.COMPLAINT;
            case "Delivery" -> MailEventType.DELIVERY;
            default -> null;
        };
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String firstText(JsonNode arrayNode) {
        return arrayNode.isArray() && arrayNode.size() > 0 ? arrayNode.get(0).asText() : null;
    }

    private String firstTagValue(JsonNode tagsNode, String key) {
        JsonNode values = tagsNode.get(key);
        return values != null && values.isArray() && values.size() > 0 ? values.get(0).asText() : null;
    }

    private String firstDiagnosticCode(JsonNode bouncedRecipients) {
        if (bouncedRecipients != null && bouncedRecipients.isArray() && bouncedRecipients.size() > 0) {
            JsonNode firstRecipient = bouncedRecipients.get(0);
            return text(firstRecipient, "diagnosticCode");
        }
        return null;
    }
}