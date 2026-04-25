package com.mike.leadfarmfinder.service.outreach.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailEventProcessingService {

    private final MailEventClassificationService classificationService;
    private final LeadDeliveryStatusService leadDeliveryStatusService;

    public void process(MailEventMessage message) {
        ClassifiedMailEvent classified = classificationService.classify(message);

        log.info("Processing mail event: type={}, leadId={}, email={}, sesMessageId={}, classifiedStatus={}, terminalFailure={}, reason={}",
                message.getEventType(),
                message.getLeadId(),
                message.getLeadEmail(),
                message.getSesMessageId(),
                classified.getDeliveryStatus(),
                classified.isTerminalFailure(),
                classified.getClassificationReason());

        leadDeliveryStatusService.updateLead(classified);
    }
}