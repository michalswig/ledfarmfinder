package com.mike.leadfarmfinder.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "leadfinder.agrarjobboerse")
public class AgrarjobboerseProperties {

    private boolean enabled = false;
    private boolean dryRun = true;

    private String startUrl;

    /** ile stron listy wyników przerobić w jednym runie */
    private int maxPagesPerRun = 1;

    /** ile ofert max zebrać (limit bezpieczeństwa) */
    private int maxOffersPerRun = 10;

    /** jitter między akcjami (anty-bot / stabilizacja UI) */
    private int minDelayMs = 800;
    private int maxDelayMs = 1400;

    /** timeout dla nawigacji/ładowania strony */
    private int pageTimeoutMs = 30000;

    /** timeout dla kliknięć (checkboxy, next, anzeigen) */
    private int clickTimeoutMs = 3000;
}
