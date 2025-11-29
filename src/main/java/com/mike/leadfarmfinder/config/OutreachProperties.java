package com.mike.leadfarmfinder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "outreach")
public record OutreachProperties(
        String fromAddress,
        String baseUnsubscribeUrl,
        String subject,
        Boolean enabled
) {}
