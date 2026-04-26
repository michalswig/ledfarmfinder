package com.mike.leadfarmfinder.service.outreach.event;

import com.mike.leadfarmfinder.entity.FarmLead;
import com.mike.leadfarmfinder.repository.FarmLeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadDeliveryStatusService {

    private final FarmLeadRepository farmLeadRepository;

    @Transactional
    public void updateLead(ClassifiedMailEvent classifiedEvent) {
        MailEventMessage event = classifiedEvent.getOriginalEvent();

        Optional<FarmLead> leadOpt = findLead(event);

        if (leadOpt.isEmpty()) {
            log.warn("Lead delivery update ignored - lead not found. leadId={}, email={}, sesMessageId={}, classifiedStatus={}",
                    event.getLeadId(),
                    event.getLeadEmail(),
                    event.getSesMessageId(),
                    classifiedEvent.getDeliveryStatus());
            return;
        }

        FarmLead lead = leadOpt.get();
        MailDeliveryStatus newStatus = classifiedEvent.getDeliveryStatus();

        if (isDeliveredAfterTerminalFailure(lead, newStatus)) {
            log.warn("Delivery event ignored because lead already has terminal failure. leadId={}, email={}, currentStatus={}, incomingStatus={}, sesMessageId={}",
                    lead.getId(),
                    lead.getEmail(),
                    lead.getLastDeliveryStatus(),
                    newStatus,
                    event.getSesMessageId());
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        lead.setLastDeliveryStatus(newStatus.name());
        lead.setLastDeliveryEventAt(now);
        lead.setDeliveryProviderMessageId(event.getSesMessageId());

        switch (newStatus) {
            case DELIVERED -> applyDelivered(lead);

            case DNS_FAILURE,
                 MAILBOX_NOT_FOUND,
                 DOMAIN_NOT_FOUND,
                 HARD_BOUNCE,
                 UNKNOWN_FAILURE -> applyTerminalBounce(lead, event, classifiedEvent, now);

            case SOFT_BOUNCE -> applySoftBounce(lead, event, classifiedEvent, now);

            case COMPLAINT -> applyComplaint(lead, event, classifiedEvent, now);

            case SPAM_BLOCK -> applySpamBlock(lead, event, classifiedEvent, now);
        }

        farmLeadRepository.save(lead);

        log.info("Lead delivery state updated. leadId={}, email={}, status={}, active={}, bounce={}, reviewRequired={}",
                lead.getId(),
                lead.getEmail(),
                lead.getLastDeliveryStatus(),
                lead.isActive(),
                lead.isBounce(),
                lead.isReviewRequired());
    }

    private Optional<FarmLead> findLead(MailEventMessage event) {
        if (event.getLeadId() != null && !event.getLeadId().isBlank()) {
            try {
                Long id = Long.valueOf(event.getLeadId());
                return farmLeadRepository.findById(id);
            } catch (NumberFormatException e) {
                log.warn("Invalid leadId in mail event: {}", event.getLeadId());
            }
        }

        if (event.getLeadEmail() != null && !event.getLeadEmail().isBlank()) {
            return farmLeadRepository.findByEmailIgnoreCase(event.getLeadEmail());
        }

        return Optional.empty();
    }

    private boolean isDeliveredAfterTerminalFailure(FarmLead lead, MailDeliveryStatus incomingStatus) {
        return incomingStatus == MailDeliveryStatus.DELIVERED
                && isTerminalFailureStatus(lead.getLastDeliveryStatus());
    }

    private boolean isTerminalFailureStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }

        try {
            MailDeliveryStatus currentStatus = MailDeliveryStatus.valueOf(status);

            return switch (currentStatus) {
                case DNS_FAILURE,
                     MAILBOX_NOT_FOUND,
                     DOMAIN_NOT_FOUND,
                     HARD_BOUNCE,
                     COMPLAINT,
                     SPAM_BLOCK,
                     UNKNOWN_FAILURE -> true;

                case DELIVERED,
                     SOFT_BOUNCE -> false;
            };
        } catch (IllegalArgumentException e) {
            log.warn("Unknown stored delivery status: {}", status);
            return false;
        }
    }

    private void applyDelivered(FarmLead lead) {
        lead.setReviewRequired(false);
    }

    private void applyTerminalBounce(FarmLead lead,
                                     MailEventMessage event,
                                     ClassifiedMailEvent classifiedEvent,
                                     LocalDateTime now) {
        lead.setBounce(true);
        lead.setActive(false);
        lead.setBounceType(classifiedEvent.getDeliveryStatus().name());
        lead.setBounceReason(resolveBounceReason(event, classifiedEvent));
        lead.setLastBounceAt(now);
        lead.setReviewRequired(false);

        log.info("Terminal bounce applied. leadId={}, email={}, status={}, reason={}",
                lead.getId(),
                lead.getEmail(),
                classifiedEvent.getDeliveryStatus(),
                classifiedEvent.getClassificationReason());
    }

    private void applySoftBounce(FarmLead lead,
                                 MailEventMessage event,
                                 ClassifiedMailEvent classifiedEvent,
                                 LocalDateTime now) {
        lead.setBounce(true);
        lead.setBounceType(classifiedEvent.getDeliveryStatus().name());
        lead.setBounceReason(resolveBounceReason(event, classifiedEvent));
        lead.setLastBounceAt(now);
        lead.setReviewRequired(false);
    }

    private void applyComplaint(FarmLead lead,
                                MailEventMessage event,
                                ClassifiedMailEvent classifiedEvent,
                                LocalDateTime now) {
        lead.setBounce(true);
        lead.setActive(false);
        lead.setBounceType(classifiedEvent.getDeliveryStatus().name());
        lead.setBounceReason(resolveBounceReason(event, classifiedEvent));
        lead.setLastBounceAt(now);
        lead.setReviewRequired(true);
    }

    private void applySpamBlock(FarmLead lead,
                                MailEventMessage event,
                                ClassifiedMailEvent classifiedEvent,
                                LocalDateTime now) {
        lead.setBounce(true);
        lead.setActive(false);
        lead.setBounceType(classifiedEvent.getDeliveryStatus().name());
        lead.setBounceReason(resolveBounceReason(event, classifiedEvent));
        lead.setLastBounceAt(now);
        lead.setReviewRequired(true);
    }

    private String resolveBounceReason(MailEventMessage event, ClassifiedMailEvent classifiedEvent) {
        if (event.getDiagnosticCode() != null && !event.getDiagnosticCode().isBlank()) {
            return trimTo1000(event.getDiagnosticCode());
        }
        if (classifiedEvent.getClassificationReason() != null && !classifiedEvent.getClassificationReason().isBlank()) {
            return trimTo1000(classifiedEvent.getClassificationReason());
        }
        return null;
    }

    private String trimTo1000(String value) {
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}