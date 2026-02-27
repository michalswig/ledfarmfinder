package com.mike.leadfarmfinder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

@ConfigurationProperties(prefix = "leadfinder.email")
public record EmailExtractorProperties(
        boolean mxCheckEnabled,
        String mxUnknownPolicy,
        long mxTimeoutMs,
        Set<String> knownTlds
) {}
