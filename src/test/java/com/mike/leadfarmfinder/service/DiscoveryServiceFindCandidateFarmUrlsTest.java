package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.config.LeadFinderProperties;
import com.mike.leadfarmfinder.dto.FarmClassificationResult;
import com.mike.leadfarmfinder.entity.SerpQueryCursor;
import com.mike.leadfarmfinder.service.discovery.DiscoveredUrlWriter;
import com.mike.leadfarmfinder.service.discovery.DiscoveryDuplicateChecker;
import com.mike.leadfarmfinder.service.discovery.DiscoveryQueryProvider;
import com.mike.leadfarmfinder.service.discovery.DiscoveryQueryScheduler;
import com.mike.leadfarmfinder.service.discovery.DiscoveryRunStatsWriter;
import com.mike.leadfarmfinder.service.discovery.DiscoverySnippetFetcher;
import com.mike.leadfarmfinder.service.discovery.DiscoveryUrlFilter;
import com.mike.leadfarmfinder.service.discovery.DiscoveryUrlNormalizer;
import com.mike.leadfarmfinder.service.discovery.DiscoveryUrlScorer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscoveryServiceFindCandidateFarmUrlsTest {

    @Mock private DiscoveryUrlNormalizer urlNormalizer;
    @Mock private DiscoveryUrlFilter discoveryUrlFilter;
    @Mock private DiscoveryUrlScorer urlScorer;
    @Mock private DiscoverySnippetFetcher snippetFetcher;
    @Mock private DiscoveryDuplicateChecker duplicateChecker;
    @Mock private DiscoveredUrlWriter discoveredUrlWriter;
    @Mock private DiscoveryRunStatsWriter discoveryRunStatsWriter;
    @Mock private DiscoveryQueryScheduler queryScheduler;
    @Mock private SerpApiService serpApiService;
    @Mock private OpenAiFarmClassifier farmClassifier;
    @Mock private LeadFinderProperties leadFinderProperties;
    @Mock private FarmScraperService farmScraperService;
    @Mock private DiscoveryQueryProvider discoveryQueryProvider;

    private DiscoveryService discoveryService;

    @BeforeEach
    void setUp() {
        discoveryService = new DiscoveryService(
                urlNormalizer,
                discoveryUrlFilter,
                urlScorer,
                snippetFetcher,
                duplicateChecker,
                discoveredUrlWriter,
                discoveryRunStatsWriter,
                queryScheduler,
                farmScraperService,
                serpApiService,
                farmClassifier,
                leadFinderProperties,
                discoveryQueryProvider
        );
    }

    @Nested
    @DisplayName("findCandidateFarmUrls")
    class FindCandidateFarmUrlsTests {

        @Test
        @DisplayName("should throw when discovery queries are not configured")
        void shouldThrowWhenDiscoveryQueriesAreNotConfigured() {
            LeadFinderProperties.Discovery discovery = new LeadFinderProperties.Discovery();
            discovery.setQueries(List.of());

            when(leadFinderProperties.getDiscovery()).thenReturn(discovery);
            when(discoveryQueryProvider.getQueries()).thenReturn(List.of());

            assertThatThrownBy(() -> discoveryService.findCandidateFarmUrls(5))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Discovery queries are not configured");
        }

        @Test
        @DisplayName("should return empty list when all queries are exhausted")
        void shouldReturnEmptyListWhenAllQueriesAreExhausted() {
            LeadFinderProperties.Discovery discovery = baseDiscoveryConfig();
            discovery.setQueries(List.of("q1"));

            when(leadFinderProperties.getDiscovery()).thenReturn(discovery);
            when(discoveryQueryProvider.getQueries()).thenReturn(List.of("q1"));
            when(queryScheduler.pickNextNonExhaustedQuery(List.of("q1")))
                    .thenReturn(Optional.empty());

            List<String> result = discoveryService.findCandidateFarmUrls(5);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty list when limit is 0")
        void shouldReturnEmptyListWhenLimitIsZero() {
            List<String> result = discoveryService.findCandidateFarmUrls(0);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should accept farm url when classifier returns isFarm=true")
        void shouldAcceptFarmUrlWhenClassifierReturnsFarm() {
            LeadFinderProperties.Discovery discovery = baseDiscoveryConfig();
            discovery.setQueries(List.of("q1"));

            SerpQueryCursor cursor = cursor("q1", 1, 5);

            when(leadFinderProperties.getDiscovery()).thenReturn(discovery);
            when(discoveryQueryProvider.getQueries()).thenReturn(List.of("q1"));
            when(queryScheduler.pickNextNonExhaustedQuery(List.of("q1")))
                    .thenReturn(Optional.of(new DiscoveryQueryScheduler.QueryPick(0, "q1", cursor)));
            when(queryScheduler.isExhausted(cursor)).thenReturn(false);
            when(serpApiService.searchUrls(anyString(), anyInt(), anyInt()))
                    .thenReturn(List.of("https://farm.example.com"));
            when(urlNormalizer.isNotFileUrl(anyString())).thenReturn(true);
            when(urlNormalizer.normalizeUrl(anyString())).thenAnswer(i -> i.getArgument(0));
            when(urlNormalizer.extractNormalizedDomain(anyString())).thenReturn("farm.example.com");
            when(discoveryUrlFilter.isAllowedDomain(anyString())).thenReturn(true);
            when(discoveryUrlFilter.isHardNegativePath(anyString())).thenReturn(false);
            when(duplicateChecker.checkAlreadySeen(anyString(), anyString()))
                    .thenReturn(DiscoveryDuplicateChecker.SeenDecision.NOT_SEEN);
            when(urlScorer.computeDomainPriorityScore(anyString())).thenReturn(42);
            when(snippetFetcher.fetchTextSnippet(anyString())).thenReturn("some snippet text");
            when(farmClassifier.classifyFarm(anyString(), anyString()))
                    .thenReturn(new FarmClassificationResult(true, false, "farm", null));

            List<String> result = discoveryService.findCandidateFarmUrls(1);

            assertThat(result).hasSize(1);
            verify(farmClassifier, times(1)).classifyFarm(anyString(), anyString());
            verify(discoveredUrlWriter, times(1)).save(anyString(), any(FarmClassificationResult.class));
        }

        @Test
        @DisplayName("should save cursor and discovery stats after run")
        void shouldSaveCursorAndDiscoveryStatsAfterRun() {
            LeadFinderProperties.Discovery discovery = baseDiscoveryConfig();
            discovery.setQueries(List.of("q1"));

            SerpQueryCursor cursor = cursor("q1", 1, 3);

            when(leadFinderProperties.getDiscovery()).thenReturn(discovery);
            when(discoveryQueryProvider.getQueries()).thenReturn(List.of("q1"));
            when(queryScheduler.pickNextNonExhaustedQuery(List.of("q1")))
                    .thenReturn(Optional.of(new DiscoveryQueryScheduler.QueryPick(0, "q1", cursor)));
            when(queryScheduler.isExhausted(cursor)).thenReturn(false);
            when(serpApiService.searchUrls(anyString(), anyInt(), anyInt())).thenReturn(List.of());

            discoveryService.findCandidateFarmUrls(5);

            verify(queryScheduler, times(1)).saveCursorAfterRun(any(SerpQueryCursor.class), anyInt());
            verify(discoveryRunStatsWriter, times(1)).save(
                    anyString(), any(LocalDateTime.class),
                    anyInt(), anyInt(), anyInt(), anyInt(),
                    anyInt(), anyInt(), anyInt(), anyInt(), anyInt()
            );
        }
    }

    private LeadFinderProperties.Discovery baseDiscoveryConfig() {
        LeadFinderProperties.Discovery discovery = new LeadFinderProperties.Discovery();
        discovery.setResultsPerPage(10);
        discovery.setMaxPagesPerRun(1);
        discovery.setDefaultMaxSerpPage(5);
        return discovery;
    }

    private SerpQueryCursor cursor(String query, int currentPage, int maxPage) {
        SerpQueryCursor cursor = new SerpQueryCursor();
        cursor.setQuery(query);
        cursor.setCurrentPage(currentPage);
        cursor.setMaxPage(maxPage);
        return cursor;
    }
}