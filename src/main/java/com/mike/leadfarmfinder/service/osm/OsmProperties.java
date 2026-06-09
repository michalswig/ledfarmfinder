package com.mike.leadfarmfinder.service.osm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "osm")
public class OsmProperties {

    private boolean enabled = false;

    private String overpassUrl = "https://overpass-api.de/api/interpreter";

    private String bbox = "47.3,5.9,55.1,15.0";

    private int timeoutMs = 60_000;

    private int maxUrlsPerRun = 300;

    private String cron = "0 2 * * SUN";
}