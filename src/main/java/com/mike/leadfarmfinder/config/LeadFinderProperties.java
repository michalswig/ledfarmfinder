package com.mike.leadfarmfinder.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "leadfinder")
public class LeadFinderProperties {

    private Discovery discovery = new Discovery();
    private Scraper scraper = new Scraper();
    private QueryCycle queryCycle = new QueryCycle();

    @Data
    public static class Discovery {
        private int resultsPerPage = 10;
        private int maxPagesPerRun = 2;
        private int defaultMaxSerpPage = 2;
        private int limitPerRun = 100;
        private int queriesPerRun = 1;
        private List<String> queries = List.of();
    }

    @Data
    public static class Scraper {
        private long minHoursBetweenScrapes = 12;
    }

    @Data
    public static class QueryCycle {
        private String cron = "0 0 3 * * SUN";
        private int scoreThreshold = 40;
        private int testLimit = 5;
    }
}