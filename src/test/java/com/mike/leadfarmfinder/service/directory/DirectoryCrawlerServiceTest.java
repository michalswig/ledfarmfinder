package com.mike.leadfarmfinder.service.directory;

import com.mike.leadfarmfinder.dto.FarmClassificationResult;
import com.mike.leadfarmfinder.service.FarmScraperService;
import com.mike.leadfarmfinder.service.discovery.DiscoveredUrlWriter;
import com.mike.leadfarmfinder.service.discovery.DiscoveryDuplicateChecker;
import com.mike.leadfarmfinder.service.discovery.DiscoveryUrlNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DirectoryCrawlerServiceTest {

    @Mock private DirectorySource source;
    @Mock private DiscoveryDuplicateChecker duplicateChecker;
    @Mock private DiscoveryUrlNormalizer urlNormalizer;
    @Mock private DiscoveredUrlWriter discoveredUrlWriter;
    @Mock private FarmScraperService farmScraperService;

    private DirectoryCrawlerService service;

    @BeforeEach
    void setUp() {
        service = new DirectoryCrawlerService(
                List.of(source),
                duplicateChecker,
                urlNormalizer,
                discoveredUrlWriter,
                farmScraperService
        );
        when(source.sourceName()).thenReturn("test-source");
    }

    @Nested
    @DisplayName("crawlAll")
    class CrawlAllTests {

        @Test
        @DisplayName("skips url when duplicate checker returns SEEN_BY_URL")
        void skipsUrlWhenSeenByUrl() {
            when(source.fetchFarmUrls()).thenReturn(List.of("https://farm.de"));
            when(urlNormalizer.normalizeUrl("https://farm.de")).thenReturn("https://farm.de");
            when(urlNormalizer.extractNormalizedDomain("https://farm.de")).thenReturn("farm.de");
            when(duplicateChecker.checkAlreadySeen("https://farm.de", "farm.de"))
                    .thenReturn(DiscoveryDuplicateChecker.SeenDecision.SEEN_BY_URL);

            List<DirectoryCrawlResult> results = service.crawlAll(10);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).urlsSkippedDuplicate()).isEqualTo(1);
            assertThat(results.get(0).urlsProcessed()).isEqualTo(0);
            verify(farmScraperService, never()).scrapeFarmLeads(anyString());
        }

        @Test
        @DisplayName("scrapes and saves url when not seen before")
        void scrapesAndSavesWhenNotSeen() {
            when(source.fetchFarmUrls()).thenReturn(List.of("https://farm.de"));
            when(urlNormalizer.normalizeUrl("https://farm.de")).thenReturn("https://farm.de");
            when(urlNormalizer.extractNormalizedDomain("https://farm.de")).thenReturn("farm.de");
            when(duplicateChecker.checkAlreadySeen("https://farm.de", "farm.de"))
                    .thenReturn(DiscoveryDuplicateChecker.SeenDecision.NOT_SEEN);

            List<DirectoryCrawlResult> results = service.crawlAll(10);

            assertThat(results.get(0).urlsProcessed()).isEqualTo(1);
            assertThat(results.get(0).urlsScrapedOk()).isEqualTo(1);
            assertThat(results.get(0).urlsScrapedError()).isEqualTo(0);
            verify(farmScraperService).scrapeFarmLeads("https://farm.de");
            verify(discoveredUrlWriter).save(eq("https://farm.de"), any(FarmClassificationResult.class));
        }

        @Test
        @DisplayName("counts error and continues when scraper throws exception")
        void countsErrorAndContinuesOnScraperException() {
            when(source.fetchFarmUrls()).thenReturn(List.of("https://farm-a.de", "https://farm-b.de"));

            when(urlNormalizer.normalizeUrl("https://farm-a.de")).thenReturn("https://farm-a.de");
            when(urlNormalizer.normalizeUrl("https://farm-b.de")).thenReturn("https://farm-b.de");
            when(urlNormalizer.extractNormalizedDomain("https://farm-a.de")).thenReturn("farm-a.de");
            when(urlNormalizer.extractNormalizedDomain("https://farm-b.de")).thenReturn("farm-b.de");

            when(duplicateChecker.checkAlreadySeen("https://farm-a.de", "farm-a.de"))
                    .thenReturn(DiscoveryDuplicateChecker.SeenDecision.NOT_SEEN);
            when(duplicateChecker.checkAlreadySeen("https://farm-b.de", "farm-b.de"))
                    .thenReturn(DiscoveryDuplicateChecker.SeenDecision.NOT_SEEN);

            doThrow(new RuntimeException("timeout")).when(farmScraperService).scrapeFarmLeads("https://farm-a.de");

            List<DirectoryCrawlResult> results = service.crawlAll(10);

            assertThat(results.get(0).urlsProcessed()).isEqualTo(2);
            assertThat(results.get(0).urlsScrapedOk()).isEqualTo(1);
            assertThat(results.get(0).urlsScrapedError()).isEqualTo(1);
            verify(farmScraperService).scrapeFarmLeads("https://farm-b.de");
        }

        @Test
        @DisplayName("stops processing when budget is exhausted")
        void stopsWhenBudgetExhausted() {
            when(source.fetchFarmUrls()).thenReturn(
                    List.of("https://farm-a.de", "https://farm-b.de", "https://farm-c.de")
            );

            when(urlNormalizer.normalizeUrl(anyString())).thenAnswer(i -> i.getArgument(0));
            when(urlNormalizer.extractNormalizedDomain(anyString())).thenAnswer(i ->
                    i.getArgument(0, String.class).replace("https://", "").replace("/", "")
            );
            when(duplicateChecker.checkAlreadySeen(anyString(), anyString()))
                    .thenReturn(DiscoveryDuplicateChecker.SeenDecision.NOT_SEEN);

            List<DirectoryCrawlResult> results = service.crawlAll(2);

            assertThat(results.get(0).urlsProcessed()).isEqualTo(2);
            verify(farmScraperService, times(2)).scrapeFarmLeads(anyString());
            verify(farmScraperService, never()).scrapeFarmLeads("https://farm-c.de");
        }

        @Test
        @DisplayName("returns empty result when source throws exception")
        void returnsEmptyResultWhenSourceThrows() {
            when(source.fetchFarmUrls()).thenThrow(new RuntimeException("connection refused"));

            List<DirectoryCrawlResult> results = service.crawlAll(10);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).urlsProcessed()).isEqualTo(0);
            assertThat(results.get(0).urlsFetched()).isEqualTo(0);
            verify(farmScraperService, never()).scrapeFarmLeads(anyString());
        }
    }
}