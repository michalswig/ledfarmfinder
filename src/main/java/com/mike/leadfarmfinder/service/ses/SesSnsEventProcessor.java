package com.mike.leadfarmfinder.service.ses;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SesSnsEventProcessor {

    private final ObjectMapper objectMapper;
    private final SesLeadEventService sesLeadEventService;

    public void processSesEvent(String messageJson) {
        try {
            JsonNode root = objectMapper.readTree(messageJson);

            String eventType = text(root, "eventType");
            if (eventType == null) {
                eventType = text(root, "notificationType");
            }

            JsonNode mail = root.path("mail");
            String sesMessageId = text(mail, "messageId");
            String destination = firstText(mail.path("destination"));
            String leadId = firstTagValue(mail.path("tags"), "leadId");
            String emailType = firstTagValue(mail.path("tags"), "emailType");

            if (eventType == null || eventType.isBlank()) {
                log.warn("SES event ignored - missing eventType/notificationType. sesMessageId={}, leadId={}, destination={}, payload={}",
                        sesMessageId, leadId, destination, messageJson);
                return;
            }

            log.info("Processing SES event: type={}, sesMessageId={}, leadId={}, destination={}, emailType={}",
                    eventType, sesMessageId, leadId, destination, emailType);

            switch (eventType) {
                case "Bounce" -> {
                    String bounceType = text(root.path("bounce"), "bounceType");
                    String bounceSubType = text(root.path("bounce"), "bounceSubType");

                    log.info("SES bounce details: bounceType={}, bounceSubType={}, sesMessageId={}, leadId={}, destination={}",
                            bounceType, bounceSubType, sesMessageId, leadId, destination);

                    sesLeadEventService.handleBounce(leadId, destination, sesMessageId, bounceType, bounceSubType);
                }
                case "Complaint" -> sesLeadEventService.handleComplaint(leadId, destination, sesMessageId);
                case "Delivery" -> sesLeadEventService.handleDelivery(leadId, destination, sesMessageId);
                case "Send" -> sesLeadEventService.handleSend(leadId, destination, sesMessageId);
                default -> log.info("Ignoring SES event type={}", eventType);
            }
        } catch (Exception e) {
            log.error("Failed to process SES event payload: {}", messageJson, e);
        }
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
}