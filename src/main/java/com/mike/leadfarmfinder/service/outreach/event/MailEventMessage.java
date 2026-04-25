package com.mike.leadfarmfinder.service.outreach.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailEventMessage {

    private MailEventType eventType;

    private String leadId;
    private String leadEmail;
    private String emailType;

    private String sesMessageId;

    private String bounceType;
    private String bounceSubType;

    private String diagnosticCode;
    private String status;
    private String action;

    private String rawPayload;

    private LocalDateTime occurredAt;
}
