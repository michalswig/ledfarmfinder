package com.mike.leadfarmfinder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "leadfinder.email")
public record EmailExtractorProperties(
        boolean mxCheck,
        String mxUnknownPolicy
) {}
