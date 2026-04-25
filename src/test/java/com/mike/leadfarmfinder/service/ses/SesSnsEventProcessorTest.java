package com.mike.leadfarmfinder.service.ses;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mike.leadfarmfinder.service.outreach.event.MailEventMessage;
import com.mike.leadfarmfinder.service.outreach.event.MailEventPublisher;
import com.mike.leadfarmfinder.service.outreach.event.MailEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SesSnsEventProcessorTest {

    private ObjectMapper objectMapper;
    private MailEventPublisher publisher;
    private SesSnsEventProcessor processor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        publisher = mock(MailEventPublisher.class);
        processor = new SesSnsEventProcessor(objectMapper, publisher);
    }

    @Test
    void shouldPublishBounceEvent() {
        String json = """
                {
                  "eventType": "Bounce",
                  "mail": {
                    "messageId": "ses-123",
                    "destination": ["test@farm.com"],
                    "tags": {
                      "leadId": ["42"],
                      "emailType": ["FIRST"]
                    }
                  },
                  "bounce": {
                    "bounceType": "Permanent",
                    "bounceSubType": "General",
                    "bouncedRecipients": [
                      {
                        "diagnosticCode": "550 mailbox not found"
                      }
                    ]
                  }
                }
                """;

        processor.processSesEvent(json, json);

        ArgumentCaptor<MailEventMessage> captor = ArgumentCaptor.forClass(MailEventMessage.class);
        verify(publisher, times(1)).publish(captor.capture());

        MailEventMessage event = captor.getValue();

        assertEquals(MailEventType.BOUNCE, event.getEventType());
        assertEquals("42", event.getLeadId());
        assertEquals("test@farm.com", event.getLeadEmail());
        assertEquals("ses-123", event.getSesMessageId());
        assertEquals("Permanent", event.getBounceType());
        assertEquals("550 mailbox not found", event.getDiagnosticCode());
    }

    @Test
    void shouldIgnoreUnsupportedEvent() {
        String json = """
                {
                  "eventType": "Open",
                  "mail": {
                    "messageId": "ses-123"
                  }
                }
                """;

        processor.processSesEvent(json, json);

        verify(publisher, never()).publish(any());
    }

    @Test
    void shouldThrowExceptionForInvalidJson() {
        String invalidJson = "not-a-json";

        assertThrows(RuntimeException.class, () ->
                processor.processSesEvent(invalidJson, invalidJson)
        );

        verify(publisher, never()).publish(any());
    }

    @Test
    void shouldThrowExceptionWhenPublisherFails() {
        String json = """
                {
                  "eventType": "Delivery",
                  "mail": {
                    "messageId": "ses-123",
                    "destination": ["test@farm.com"]
                  }
                }
                """;

        doThrow(new RuntimeException("Rabbit down"))
                .when(publisher)
                .publish(any());

        assertThrows(RuntimeException.class, () ->
                processor.processSesEvent(json, json)
        );
    }
}