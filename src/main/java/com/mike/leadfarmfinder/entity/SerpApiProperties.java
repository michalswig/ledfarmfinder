package com.mike.leadfarmfinder.entity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "serpapi")
public record SerpApiProperties(
        String apiKey,
        String baseUrl,
        String defaultCountry,
        String defaultLanguage,
        String defaultEngine
) {}
