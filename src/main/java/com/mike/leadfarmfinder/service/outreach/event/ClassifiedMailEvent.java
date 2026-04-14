package com.mike.leadfarmfinder.service.outreach.event;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ClassifiedMailEvent {
    MailEventMessage originalEvent;
    MailDeliveryStatus deliveryStatus;
    boolean terminalFailure;
    String classificationReason;
}