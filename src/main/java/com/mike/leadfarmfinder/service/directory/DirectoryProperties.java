package com.mike.leadfarmfinder.service.directory;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "directory")
public record DirectoryProperties(
        boolean enabled,
        int maxUrlsPerRun
) {

    public DirectoryProperties {
        if (maxUrlsPerRun <= 0) {
            maxUrlsPerRun = 50;
        }
    }
}