package com.mike.leadfarmfinder.service.outreach.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MailEventProcessingService {

    public void process(MailEventMessage message) {

        log.info("Processing mail event: type={}, leadId={}, email={}, sesMessageId={}",
                message.getEventType(),
                message.getLeadId(),
                message.getLeadEmail(),
                message.getSesMessageId());

        //tylko wylogowanie na razie
    }
}
