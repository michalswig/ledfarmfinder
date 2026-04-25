package com.mike.leadfarmfinder.service.outreach;

import com.mike.leadfarmfinder.entity.FarmLead;
import com.mike.leadfarmfinder.repository.FarmLeadRepository;
import com.mike.leadfarmfinder.service.outreach.event.MailEventMessage;
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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MailEventIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private FarmLeadRepository repository;

    @MockitoBean
    private JavaMailSender javaMailSender;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();

        rabbitAdmin.purgeQueue("outreach.event.queue", true);
        rabbitAdmin.purgeQueue("outreach.event.retry.queue", true);
        rabbitAdmin.purgeQueue("outreach.event.dlq", true);
    }

    @Test
    void shouldMarkLeadAsBouncedOnDnsFailureEvent() {
        FarmLead lead = new FarmLead();
        lead.setEmail("info@kartoffelhof-walter.de");
        lead.setActive(true);
        lead.setBounce(false);
        repository.save(lead);

        MailEventMessage message = new MailEventMessage();
        message.setEventType(MailEventType.BOUNCE);
        message.setBounceType("Permanent");
        message.setDiagnosticCode("421 4.4.0 Unable to lookup DNS for kartoffelhof-walter.de");
        message.setLeadEmail("info@kartoffelhof-walter.de");
        message.setSesMessageId("test-ses-message-dns-1");

        rabbitTemplate.convertAndSend(
                "outreach.events.exchange",
                "outreach.event",
                message
        );

        await().atMost(5, SECONDS).untilAsserted(() -> {
            FarmLead updated = repository.findByEmailIgnoreCase("info@kartoffelhof-walter.de")
                    .orElseThrow();

            assertTrue(updated.isBounce());
            assertFalse(updated.isActive());
            assertEquals("DNS_FAILURE", updated.getBounceType());
        });
    }

    @Test
    void shouldDeactivateLeadOnComplaintEvent() {
        FarmLead lead = new FarmLead();
        lead.setEmail("complaint@farm-example.de");
        lead.setActive(true);
        lead.setBounce(false);
        repository.save(lead);

        MailEventMessage message = new MailEventMessage();
        message.setEventType(MailEventType.COMPLAINT);
        message.setLeadEmail("complaint@farm-example.de");
        message.setSesMessageId("test-ses-message-complaint-1");

        rabbitTemplate.convertAndSend(
                "outreach.events.exchange",
                "outreach.event",
                message
        );

        await().atMost(5, SECONDS).untilAsserted(() -> {
            FarmLead updated = repository.findByEmailIgnoreCase("complaint@farm-example.de")
                    .orElseThrow();

            assertTrue(updated.isBounce());
            assertFalse(updated.isActive());
            assertEquals("COMPLAINT", updated.getBounceType());
            assertTrue(updated.isReviewRequired());
        });
    }

    @Test
    void shouldKeepLeadActiveOnTransientSoftBounceEvent() {
        FarmLead lead = new FarmLead();
        lead.setEmail("softbounce@farm-example.de");
        lead.setActive(true);
        lead.setBounce(false);
        repository.save(lead);

        MailEventMessage message = new MailEventMessage();
        message.setEventType(MailEventType.BOUNCE);
        message.setBounceType("Transient");
        message.setDiagnosticCode("451 4.2.0 Mailbox temporarily unavailable");
        message.setLeadEmail("softbounce@farm-example.de");
        message.setSesMessageId("test-ses-message-softbounce-1");

        rabbitTemplate.convertAndSend(
                "outreach.events.exchange",
                "outreach.event",
                message
        );

        await().atMost(5, SECONDS).untilAsserted(() -> {
            FarmLead updated = repository.findByEmailIgnoreCase("softbounce@farm-example.de")
                    .orElseThrow();

            assertTrue(updated.isBounce());
            assertTrue(updated.isActive());
            assertEquals("SOFT_BOUNCE", updated.getBounceType());
            assertFalse(updated.isReviewRequired());
        });
    }
}