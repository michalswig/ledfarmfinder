package com.mike.leadfarmfinder.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mike.leadfarmfinder.service.ses.SesSnsEventProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/webhooks/ses")
public class SesSnsWebhookController {

    private final ObjectMapper objectMapper;
    private final SesSnsEventProcessor sesSnsEventProcessor;
    private final RestTemplate restTemplate;

    @PostMapping
    public ResponseEntity<String> handleSnsMessage(
            @RequestBody String body,
            @RequestHeader(value = "x-amz-sns-message-type", required = false) String messageTypeHeader) throws Exception {

        JsonNode root = objectMapper.readTree(body);
        String type = text(root, "Type");
        String topicArn = text(root, "TopicArn");

        log.info("SES SNS webhook received: headerType={}, bodyType={}, topicArn={}",
                messageTypeHeader, type, topicArn);

        if ("SubscriptionConfirmation".equals(type)) {
            String subscribeUrl = text(root, "SubscribeURL");

            if (subscribeUrl == null || subscribeUrl.isBlank()) {
                log.warn("SNS SubscriptionConfirmation received without SubscribeURL");
                return ResponseEntity.ok("Ignored - missing SubscribeURL");
            }

            log.info("Confirming SNS subscription via {}", subscribeUrl);
            restTemplate.getForObject(subscribeUrl, String.class);
            return ResponseEntity.ok("Subscription confirmed");
        }

        if ("Notification".equals(type)) {
            String message = text(root, "Message");
            if (message == null || message.isBlank()) {
                log.warn("SNS Notification received without Message payload");
                return ResponseEntity.ok("Ignored - empty message");
            }

            sesSnsEventProcessor.processSesEvent(message);
            return ResponseEntity.ok("Notification processed");
        }

        if ("UnsubscribeConfirmation".equals(type)) {
            log.warn("SNS sent UnsubscribeConfirmation");
            return ResponseEntity.ok("Ignored");
        }

        log.warn("Unknown SNS message type: {}", type);
        return ResponseEntity.ok("Ignored");
    }

    private String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }
}