package com.mike.leadfarmfinder.service.outreach.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Slf4j
@Service
public class MailEventClassificationService {

    public ClassifiedMailEvent classify(MailEventMessage event) {
        if (event == null || event.getEventType() == null) {
            return ClassifiedMailEvent.builder()
                    .originalEvent(event)
                    .deliveryStatus(MailDeliveryStatus.UNKNOWN_FAILURE)
                    .terminalFailure(false)
                    .classificationReason("missing event or eventType")
                    .build();
        }

        return switch (event.getEventType()) {
            case DELIVERY -> classifyDelivery(event);
            case COMPLAINT -> classifyComplaint(event);
            case BOUNCE -> classifyBounce(event);
        };
    }

    private ClassifiedMailEvent classifyDelivery(MailEventMessage event) {
        return build(event, MailDeliveryStatus.DELIVERED, false, "delivery event");
    }

    private ClassifiedMailEvent classifyComplaint(MailEventMessage event) {
        return build(event, MailDeliveryStatus.COMPLAINT, true, "complaint event");
    }

    private ClassifiedMailEvent classifyBounce(MailEventMessage event) {
        String bounceType = normalize(event.getBounceType());
        String bounceSubType = normalize(event.getBounceSubType());
        String diagnosticCode = normalize(event.getDiagnosticCode());
        String status = normalize(event.getStatus());

        if (isPermanentSmtpFailure(status)) {
            return classifyPermanentSmtpFailure(event, diagnosticCode);
        }

        if (containsAny(diagnosticCode,
                "unable to lookup dns",
                "lookup dns",
                "dns")) {
            return build(event, MailDeliveryStatus.DNS_FAILURE, true,
                    "diagnostic code indicates dns lookup failure");
        }

        if (containsAny(diagnosticCode,
                "mailbox unavailable",
                "user unknown",
                "unknown user",
                "no such user",
                "recipient address rejected",
                "mailbox not found",
                "unrouteable address",
                "invalid recipient",
                "recipient unknown",
                "unknown recipient")) {
            return build(event, MailDeliveryStatus.MAILBOX_NOT_FOUND, true,
                    "diagnostic code indicates mailbox not found");
        }

        if (containsAny(diagnosticCode,
                "domain not found",
                "no such domain",
                "host or domain name not found")) {
            return build(event, MailDeliveryStatus.DOMAIN_NOT_FOUND, true,
                    "diagnostic code indicates domain not found");
        }

        if ("suppressed".equals(bounceSubType) || "onaccountsuppressionlist".equals(bounceSubType)) {
            return build(event, MailDeliveryStatus.SPAM_BLOCK, true,
                    "bounce subtype indicates suppression list");
        }

        if (containsAny(diagnosticCode,
                "blocked",
                "blacklist",
                "spam",
                "policy rejection",
                "message rejected",
                "relay access denied",
                "access denied",
                "not authorized",
                "not permitted",
                "sender denied",
                "sender rejected")) {
            return build(event, MailDeliveryStatus.SPAM_BLOCK, true,
                    "diagnostic code indicates spam or policy block");
        }

        if ("permanent".equals(bounceType)) {
            return build(event, MailDeliveryStatus.HARD_BOUNCE, true,
                    "bounceType=Permanent");
        }

        if ("transient".equals(bounceType)) {
            return build(event, MailDeliveryStatus.SOFT_BOUNCE, false,
                    "bounceType=Transient");
        }

        if ("undetermined".equals(bounceType)) {
            return build(event, MailDeliveryStatus.UNKNOWN_FAILURE, true,
                    "bounceType=Undetermined");
        }

        return build(event, MailDeliveryStatus.UNKNOWN_FAILURE, true,
                "bounce could not be classified");
    }

    private ClassifiedMailEvent classifyPermanentSmtpFailure(MailEventMessage event, String diagnosticCode) {
        if (containsAny(diagnosticCode,
                "unable to lookup dns",
                "lookup dns",
                "dns")) {
            return build(event, MailDeliveryStatus.DNS_FAILURE, true,
                    "smtp status indicates permanent 5xx failure; diagnostic code indicates dns lookup failure");
        }

        if (containsAny(diagnosticCode,
                "mailbox unavailable",
                "user unknown",
                "unknown user",
                "no such user",
                "recipient address rejected",
                "mailbox not found",
                "unrouteable address",
                "invalid recipient",
                "recipient unknown",
                "unknown recipient")) {
            return build(event, MailDeliveryStatus.MAILBOX_NOT_FOUND, true,
                    "smtp status indicates permanent 5xx failure; diagnostic code indicates mailbox not found");
        }

        if (containsAny(diagnosticCode,
                "domain not found",
                "no such domain",
                "host or domain name not found")) {
            return build(event, MailDeliveryStatus.DOMAIN_NOT_FOUND, true,
                    "smtp status indicates permanent 5xx failure; diagnostic code indicates domain not found");
        }

        if (containsAny(diagnosticCode,
                "blocked",
                "blacklist",
                "spam",
                "policy rejection",
                "message rejected",
                "relay access denied",
                "access denied",
                "not authorized",
                "not permitted",
                "sender denied",
                "sender rejected")) {
            return build(event, MailDeliveryStatus.SPAM_BLOCK, true,
                    "smtp status indicates permanent 5xx failure; diagnostic code indicates spam or policy block");
        }

        return build(event, MailDeliveryStatus.UNKNOWN_FAILURE, true,
                "smtp status indicates permanent 5xx failure");
    }

    private boolean isPermanentSmtpFailure(String status) {
        return status.startsWith("5.");
    }

    private ClassifiedMailEvent build(MailEventMessage event,
                                      MailDeliveryStatus status,
                                      boolean terminalFailure,
                                      String reason) {
        return ClassifiedMailEvent.builder()
                .originalEvent(event)
                .deliveryStatus(status)
                .terminalFailure(terminalFailure)
                .classificationReason(reason)
                .build();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private boolean containsAny(String value, String... fragments) {
        for (String fragment : fragments) {
            if (value.contains(fragment)) {
                return true;
            }
        }
        return false;
    }
}