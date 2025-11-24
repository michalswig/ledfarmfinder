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
        /**
         * Ile wyników na stronę pobieramy z SERP (SerpAPI).
         */
        private int resultsPerPage = 10;

        /**
         * Maksymalna liczba stron SERP przetwarzanych w jednym runie.
         */
        private int maxPagesPerRun = 3;

        /**
         * Górna granica paginacji SERP – po jej przekroczeniu zawijamy do 1.
         */
        private int defaultMaxSerpPage = 50;

        /**
         * Ile maksymalnie zaakceptowanych URL-i chcemy zebrać w jednym runie.
         * (przyda się w LeadCronJob).
         */
        private int limitPerRun = 100;

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