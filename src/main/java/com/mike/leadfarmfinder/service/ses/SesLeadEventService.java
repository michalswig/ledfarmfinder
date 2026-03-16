package com.mike.leadfarmfinder.service.ses;

import com.mike.leadfarmfinder.entity.FarmLead;
import com.mike.leadfarmfinder.repository.FarmLeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SesLeadEventService {

    private final FarmLeadRepository farmLeadRepository;

    public void handleBounce(String leadId, String destination, String sesMessageId, String bounceType, String bounceSubType) {
        Optional<FarmLead> leadOpt = findLead(leadId, destination);

        if (leadOpt.isEmpty()) {
            log.warn("Bounce event ignored - lead not found. leadId={}, destination={}, sesMessageId={}",
                    leadId, destination, sesMessageId);
            return;
        }

        FarmLead lead = leadOpt.get();
        lead.setBounce(true);
        lead.setActive(false);

        farmLeadRepository.save(lead);

        log.info("Bounce processed. leadId={}, destination={}, bounceType={}, bounceSubType={}, sesMessageId={}",
                lead.getId(), destination, bounceType, bounceSubType, sesMessageId);
    }

    public void handleComplaint(String leadId, String destination, String sesMessageId) {
        Optional<FarmLead> leadOpt = findLead(leadId, destination);

        if (leadOpt.isEmpty()) {
            log.warn("Complaint event ignored - lead not found. leadId={}, destination={}, sesMessageId={}",
                    leadId, destination, sesMessageId);
            return;
        }

        FarmLead lead = leadOpt.get();
        lead.setActive(false);

        farmLeadRepository.save(lead);

        log.info("Complaint processed. leadId={}, destination={}, sesMessageId={}",
                lead.getId(), destination, sesMessageId);
    }

    public void handleDelivery(String leadId, String destination, String sesMessageId) {
        log.info("Delivery processed. leadId={}, destination={}, sesMessageId={}",
                leadId, destination, sesMessageId);
    }

    public void handleSend(String leadId, String destination, String sesMessageId) {
        log.info("Send processed. leadId={}, destination={}, sesMessageId={}",
                leadId, destination, sesMessageId);
    }

    private Optional<FarmLead> findLead(String leadId, String destination) {
        if (leadId != null && !leadId.isBlank()) {
            try {
                Long id = Long.valueOf(leadId);
                return farmLeadRepository.findById(id);
            } catch (NumberFormatException e) {
                log.warn("Invalid leadId tag value: {}", leadId);
            }
        }

        if (destination != null && !destination.isBlank()) {
            return farmLeadRepository.findByEmailIgnoreCase(destination);
        }

        return Optional.empty();
    }
}