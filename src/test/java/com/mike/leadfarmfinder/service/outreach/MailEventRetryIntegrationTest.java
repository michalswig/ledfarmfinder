package com.mike.leadfarmfinder.service.outreach;

import com.mike.leadfarmfinder.service.outreach.event.MailEventMessage;
import com.mike.leadfarmfinder.service.outreach.event.MailEventProcessingService;
import com.mike.leadfarmfinder.service.outreach.event.MailEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MailEventRetryIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @MockitoBean
    private JavaMailSender javaMailSender;

    @MockitoBean
    private MailEventProcessingService mailEventProcessingService;

    @BeforeEach
    void cleanQueues() {
        purgeIfExists("outreach.event.queue");
        purgeIfExists("outreach.event.retry.queue");
        purgeIfExists("outreach.event.dlq");
    }

    @Test
    void shouldMoveValidMessageToDlqAfterMaxRetryAttempts() {
        String sesMessageId = "retry-test-" + UUID.randomUUID();
        AtomicInteger attempts = new AtomicInteger(0);

        doAnswer(invocation -> {
            MailEventMessage msg = invocation.getArgument(0);

            if (sesMessageId.equals(msg.getSesMessageId())) {
                attempts.incrementAndGet();
            }

            throw new RuntimeException("Forced processing failure");
        }).when(mailEventProcessingService).process(any(MailEventMessage.class));

        MailEventMessage message = MailEventMessage.builder()
                .eventType(MailEventType.BOUNCE)
                .leadEmail("retry-test@farm-example.de")
                .sesMessageId(sesMessageId)
                .bounceType("Permanent")
                .diagnosticCode("Forced retry test")
                .build();

        rabbitTemplate.convertAndSend(
                "outreach.events.exchange",
                "outreach.event",
                message
        );

        await().atMost(10, SECONDS).untilAsserted(() ->
                assertTrue(attempts.get() >= 3)
        );

        await().atMost(5, SECONDS).untilAsserted(() -> {
            Object dlqMessage = rabbitTemplate.receiveAndConvert("outreach.event.dlq");
            assertNotNull(dlqMessage);
        });
    }

    private void purgeIfExists(String queueName) {
        try {
            rabbitAdmin.purgeQueue(queueName, false);
        } catch (Exception ignored) {
        }
    }
}