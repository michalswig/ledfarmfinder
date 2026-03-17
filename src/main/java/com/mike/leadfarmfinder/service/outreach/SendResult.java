package com.mike.leadfarmfinder.service.outreach;

public record SendResult(
        boolean sent,
        boolean hardBounce,
        String providerMessageId
) {
    public SendResult(boolean sent, boolean hardBounce) {
        this(sent, hardBounce, null);
    }
}
