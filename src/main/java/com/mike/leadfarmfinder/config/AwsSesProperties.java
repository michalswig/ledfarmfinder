package com.mike.leadfarmfinder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.ses")
public record AwsSesProperties(
        String region,
        String configurationSet
){}
