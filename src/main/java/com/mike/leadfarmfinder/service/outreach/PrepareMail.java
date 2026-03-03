package com.mike.leadfarmfinder.service.outreach;

public record PrepareMail(
        String from,
        String to,
        String subject,
        String body,
        String unsubscribeUrl
) {
}
