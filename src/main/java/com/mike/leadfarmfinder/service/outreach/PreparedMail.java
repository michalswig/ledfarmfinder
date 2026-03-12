package com.mike.leadfarmfinder.service.outreach;


public record PreparedMail(
        String from,
        String to,
        String subject,
        String body,
        String unsubscribeUrl
) {
}
