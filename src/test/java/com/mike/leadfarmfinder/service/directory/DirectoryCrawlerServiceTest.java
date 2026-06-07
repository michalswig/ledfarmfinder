package com.mike.leadfarmfinder.service.directory;

import com.mike.leadfarmfinder.dto.FarmClassificationResult;
import com.mike.leadfarmfinder.service.FarmScraperService;
import com.mike.leadfarmfinder.service.OpenAiFarmClassifier;
import com.mike.leadfarmfinder.service.discovery.DiscoveredUrlWriter;
import com.mike.leadfarmfinder.service.discovery.DiscoveryDuplicateChecker;
import com.mike.leadfarmfinder.service.discovery.DiscoverySnippetFetcher;
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
    @Mock private DiscoverySnippetFetcher snippetFetcher;
    @Mock private OpenAiFarmClassifier farmClassifier;

    private DirectoryCrawlerService service;

    private static final FarmClassificationResult IS_FARM =
            new FarmClassificationResult(true, false, "single-farm-website", null);
    private static final FarmClassificationResult NOT_FARM =
            new FarmClassificationResult(false, false, "city-government-website", null);

    @BeforeEach
    void setUp() {
        service = new DirectoryCrawlerService(
                List.of(source),
                duplicateChecker,
                urlNormalizer,
                discoveredUrlWriter,
                farmScraperService,
                snippetFetcher,
                farmClassifier
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

            assertThat(results.get(0).urlsSkippedDuplicate()).isEqualTo(1);
            assertThat(results.get(0).urlsProcessed()).isEqualTo(0);
            verify(snippetFetcher, never()).fetchTextSnippet(anyString());
            verify(farmScraperService, never()).scrapeFarmLeads(anyString());
        }

        @Test
        @DisplayName("classifies, saves, and scrapes when url is new and classifier accepts")
        void scrapesWhenNotSeenAndClassifierAccepts() {
            when(source.fetchFarmUrls()).thenReturn(List.of("https://farm.de"));
            when(urlNormalizer.normalizeUrl("https://farm.de")).thenReturn("https://farm.de");
            when(urlNormalizer.extractNormalizedDomain("https://farm.de")).thenReturn("farm.de");
            when(duplicateChecker.checkAlreadySeen("https://farm.de", "farm.de"))
                    .thenReturn(DiscoveryDuplicateChecker.SeenDecision.NOT_SEEN);
            when(snippetFetcher.fetchTextSnippet("https://farm.de")).thenReturn("Hofladen Bayern Direktverkauf");
            when(farmClassifier.classifyFarm("https://farm.de", "Hofladen Bayern Direktverkauf"))
                    .thenReturn(IS_FARM);

            List<DirectoryCrawlResult> results = service.crawlAll(10);

            assertThat(results.get(0).urlsProcessed()).isEqualTo(1);
            assertThat(results.get(0).urlsScrapedOk()).isEqualTo(1);
            assertThat(results.get(0).urlsRejectedByClassifier()).isEqualTo(0);
            verify(discoveredUrlWriter).save("https://farm.de", IS_FARM);
            verify(farmScraperService).scrapeFarmLeads("https://farm.de");
        }

        @Test
        @DisplayName("saves to discovered_urls but skips scraper when classifier rejects")
        void savesButSkipsScraperWhenClassifierRejects() {
            when(source.fetchFarmUrls()).thenReturn(List.of("https://stadtportal.de"));
            when(urlNormalizer.normalizeUrl("https://stadtportal.de")).thenReturn("https://stadtportal.de");
            when(urlNormalizer.extractNormalizedDomain("https://stadtportal.de")).thenReturn("stadtportal.de");
            when(duplicateChecker.checkAlreadySeen("https://stadtportal.de", "stadtportal.de"))
                    .thenReturn(DiscoveryDuplicateChecker.SeenDecision.NOT_SEEN);
            when(snippetFetcher.fetchTextSnippet("https://stadtportal.de")).thenReturn("Stadtverwaltung Bürgermeister");
            when(farmClassifier.classifyFarm("https://stadtportal.de", "Stadtverwaltung Bürgermeister"))
                    .thenReturn(NOT_FARM);

            List<DirectoryCrawlResult> results = service.crawlAll(10);

            assertThat(results.get(0).urlsProcessed()).isEqualTo(1);
            assertThat(results.get(0).urlsRejectedByClassifier()).isEqualTo(1);
            assertThat(results.get(0).urlsScrapedOk()).isEqualTo(0);
            // discoveredUrlWriter MUSI być wywołany nawet przy rejekcji — dedup wymaga
            verify(discoveredUrlWriter).save("https://stadtportal.de", NOT_FARM);
            verify(farmScraperService, never()).scrapeFarmLeads(anyString());
        }

        @Test
        @DisplayName("counts error and continues when scraper throws exception")
        void countsErrorAndContinuesOnScraperException() {
            when(source.fetchFarmUrls()).thenReturn(List.of("https://farm-a.de", "https://farm-b.de"));
            when(urlNormalizer.normalizeUrl(anyString())).thenAnswer(i -> i.getArgument(0));
            when(urlNormalizer.extractNormalizedDomain(anyString())).thenAnswer(i ->
                    i.getArgument(0, String.class).replace("https://", ""));
            when(duplicateChecker.checkAlreadySeen(anyString(), anyString()))
                    .thenReturn(DiscoveryDuplicateChecker.SeenDecision.NOT_SEEN);
            when(snippetFetcher.fetchTextSnippet(anyString())).thenReturn("Hofladen Bayern");
            when(farmClassifier.classifyFarm(anyString(), anyString())).thenReturn(IS_FARM);
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
                    i.getArgument(0, String.class).replace("https://", ""));
            when(duplicateChecker.checkAlreadySeen(anyString(), anyString()))
                    .thenReturn(DiscoveryDuplicateChecker.SeenDecision.NOT_SEEN);
            when(snippetFetcher.fetchTextSnippet(anyString())).thenReturn("Hofladen Bayern");
            when(farmClassifier.classifyFarm(anyString(), anyString())).thenReturn(IS_FARM);

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