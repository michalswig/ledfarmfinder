package com.mike.leadfarmfinder.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mike.leadfarmfinder.service.ses.SesSnsEventProcessor;
import com.mike.leadfarmfinder.service.ses.exception.SesEventBadRequestException;
import com.mike.leadfarmfinder.service.ses.exception.SesEventProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
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
            @RequestHeader(value = "x-amz-sns-message-type", required = false) String messageTypeHeader) {

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            log.warn("Invalid SNS webhook JSON received", e);
            return ResponseEntity.badRequest().body("Invalid SNS JSON");
        }

        log.info("SNS RAW BODY: {}", body);

        String type = text(root, "Type");
        String topicArn = text(root, "TopicArn");

        log.info("SES SNS webhook received: headerType={}, bodyType={}, topicArn={}",
                messageTypeHeader, type, topicArn);

        try {
            if ("SubscriptionConfirmation".equals(type)) {
                return handleSubscriptionConfirmation(root);
            }

            if ("Notification".equals(type)) {
                return handleNotification(root, body);
            }

            if ("UnsubscribeConfirmation".equals(type)) {
                log.warn("SNS sent UnsubscribeConfirmation. topicArn={}", topicArn);
                return ResponseEntity.ok("Ignored unsubscribe confirmation");
            }

            log.warn("Unknown SNS message type: {}", type);
            return ResponseEntity.ok("Ignored unknown SNS message type");

        } catch (SesEventBadRequestException e) {
            log.warn("SES SNS notification rejected: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (SesEventProcessingException e) {
            log.error("SES SNS notification processing failed. Returning 500 so SNS can retry.", e);
            return ResponseEntity.internalServerError().body("Temporary processing failure");

        } catch (RestClientException e) {
            log.error("SNS subscription confirmation failed. Returning 500.", e);
            return ResponseEntity.internalServerError().body("Subscription confirmation failed");
        }
    }

    private ResponseEntity<String> handleSubscriptionConfirmation(JsonNode root) {
        String subscribeUrl = text(root, "SubscribeURL");

        if (subscribeUrl == null || subscribeUrl.isBlank()) {
            log.warn("SNS SubscriptionConfirmation received without SubscribeURL");
            return ResponseEntity.badRequest().body("Missing SubscribeURL");
        }

        log.info("Confirming SNS subscription via {}", subscribeUrl);
        restTemplate.getForObject(subscribeUrl, String.class);

        return ResponseEntity.ok("Subscription confirmed");
    }

    private ResponseEntity<String> handleNotification(JsonNode root, String rawPayload) {
        String message = text(root, "Message");

        if (message == null || message.isBlank()) {
            log.warn("SNS Notification received without Message payload");
            return ResponseEntity.badRequest().body("Missing SNS Message");
        }

        sesSnsEventProcessor.processSesEvent(message, rawPayload);
        return ResponseEntity.ok("Notification processed");
    }

    private String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }
}