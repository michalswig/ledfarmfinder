package com.mike.leadfarmfinder.service.outreach.event;

import com.mike.leadfarmfinder.config.LeadFinderRabbitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutreachMailEventListener {

    private final MailEventProcessingService processingService;
    private final RabbitRetrySupport retrySupport;
    private final LeadFinderRabbitProperties rabbitProperties;
    private final MailEventDlqPublisher dlqPublisher;

    @RabbitListener(queues = "${leadfinder.rabbit.outreach-events-queue}")
    public void handle(MailEventMessage message, Message amqpMessage) {
        try {
            processingService.process(message);

        } catch (Exception e) {
            long retryCount = retrySupport.getRetryCount(
                    amqpMessage,
                    rabbitProperties.getOutreachEventsRetryQueue()
            );

            log.error("Processing failed. retryCount={}, max={}, leadId={}",
                    retryCount,
                    rabbitProperties.getMaxRetryAttempts(),
                    message.getLeadId(),
                    e
            );

            if (retryCount >= rabbitProperties.getMaxRetryAttempts()) {
                dlqPublisher.publishToDlq(message);
                return;
            }

            throw new AmqpRejectAndDontRequeueException(
                    "Processing failed, sending message to retry queue",
                    e
            );
        }
    }
}