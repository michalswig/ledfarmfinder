package com.mike.leadfarmfinder.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "leadfinder")
public class LeadFinderProperties {

    private Discovery discovery = new Discovery();
    private Scraper scraper = new Scraper();

    @Data
    public static class Discovery {

        private int resultsPerPage = 10;

        private int maxPagesPerRun = 3;

        private int defaultMaxSerpPage = 10;

        private int limitPerRun = 100;

        private int queriesPerRun = 1;

        private List<String> queries = List.of();
    }

    @Data
    public static class Scraper {
        /**
         * Minimalna liczba godzin między kolejnymi scrapowaniami tej samej domeny.
         */
        private long minHoursBetweenScrapes = 12;
    }
}