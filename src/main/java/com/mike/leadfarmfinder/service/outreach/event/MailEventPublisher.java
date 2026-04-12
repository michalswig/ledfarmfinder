package com.mike.leadfarmfinder.service.outreach.event;


import com.mike.leadfarmfinder.config.LeadFinderRabbitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final LeadFinderRabbitProperties rabbitProperties;

    public void publish(MailEventMessage message) {
        rabbitTemplate.convertAndSend(
                rabbitProperties.getOutreachEventsExchange(),
                rabbitProperties.getOutreachEventsRoutingKey(),
                message
        );
        log.info("Published mail event: type={}, leadId={}, email={}, sesMessageId={}",
                message.getEventType(),
                message.getLeadId(),
                message.getLeadEmail(),
                message.getSesMessageId());
    }

}
