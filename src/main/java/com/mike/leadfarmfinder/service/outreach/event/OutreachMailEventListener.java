package com.mike.leadfarmfinder.service.outreach.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutreachMailEventListener {

    private final MailEventProcessingService processingService;

    @RabbitListener(queues = "${leadfinder.rabbit.outreach-events-queue}")
    public void handle(MailEventMessage message) {

        log.info("Received mail event from queue: type={}, leadId={}, email={}, sesMessageId={}",
                message.getEventType(),
                message.getLeadId(),
                message.getLeadEmail(),
                message.getSesMessageId());

        processingService.process(message);
    }
}