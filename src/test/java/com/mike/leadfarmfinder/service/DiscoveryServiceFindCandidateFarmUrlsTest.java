package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.config.LeadFinderProperties;
import com.mike.leadfarmfinder.dto.FarmClassificationResult;
import com.mike.leadfarmfinder.entity.SerpQueryCursor;
import com.mike.leadfarmfinder.service.discovery.DiscoveredUrlWriter;
import com.mike.leadfarmfinder.service.discovery.DiscoveryDuplicateChecker;
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
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscoveryServiceFindCandidateFarmUrlsTest {

    @Mock
    private DiscoveryUrlNormalizer urlNormalizer;
    @Mock
    private DiscoveryUrlFilter discoveryUrlFilter;
    @Mock
    private DiscoveryUrlScorer urlScorer;
    @Mock
    private DiscoverySnippetFetcher snippetFetcher;
    @Mock
    private DiscoveryDuplicateChecker duplicateChecker;
    @Mock
    private DiscoveredUrlWriter discoveredUrlWriter;
    @Mock
    private DiscoveryRunStatsWriter discoveryRunStatsWriter;
    @Mock
    private DiscoveryQueryScheduler queryScheduler;
    @Mock
    private SerpApiService serpApiService;
    @Mock
    private OpenAiFarmClassifier farmClassifier;
    @Mock
    private LeadFinderProperties leadFinderProperties;

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
                serpApiService,
                farmClassifier,
                leadFinderProperties
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

            assertThatThrownBy(() -> discoveryService.findCandidateFarmUrls(5))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Discovery queries are not configured");
        }

        @Test
        @DisplayName("should return empty list when all queries are exhausted")
        void shouldReturnEmptyListWhenAllQueriesAreExhausted() {
            LeadFinderProperties.Discovery discovery = baseDiscoveryConfig();
            discovery.setQueries(List.of("q1", "q2"));

            when(leadFinderProperties.getDiscovery()).thenReturn(discovery);
            when(queryScheduler.pickNextNonExhaustedQuery(List.of("q1", "q2")))
                    .thenReturn(Optional.empty());

            List<String> result = discoveryService.findCandidateFarmUrls(5);

            assertThat(result).isEmpty();
            verify(serpApiService, never()).searchUrls(anyString(), anyInt(), anyInt());
            verify(discoveryRunStatsWriter, never()).save(
                    anyString(), any(), anyInt(), anyInt(), anyInt(),
                    anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt()
            );
        }

        @Test
        @DisplayName("should mark query done when SERP page is empty")
        void shouldMarkQueryDoneWhenSerpPageIsEmpty() {
            LeadFinderProperties.Discovery discovery = baseDiscoveryConfig();
            discovery.setQueries(List.of("q1"));

            SerpQueryCursor cursor = cursor("q1", 1, 3);

            when(leadFinderProperties.getDiscovery()).thenReturn(discovery);
            when(queryScheduler.pickNextNonExhaustedQuery(List.of("q1")))
                    .thenReturn(Optional.of(new DiscoveryQueryScheduler.QueryPick(0, "q1", cursor)));
            when(queryScheduler.isExhausted(cursor)).thenReturn(false);
            when(serpApiService.searchUrls(anyString(), anyInt(), anyInt())).thenReturn(List.of());

            List<String> result = discoveryService.findCandidateFarmUrls(5);

            assertThat(result).isEmpty();
            verify(queryScheduler).saveCursorAfterRun(cursor, 4);
            verify(discoveryRunStatsWriter).save(
                    anyString(),
                    any(LocalDateTime.class),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt()
            );
        }

        @Test
        @DisplayName("should return accepted urls when classifier marks farm and contact url exists")
        void shouldReturnAcceptedUrlsWhenClassifierMarksFarmAndContactUrlExists() {
            LeadFinderProperties.Discovery discovery = baseDiscoveryConfig();
            discovery.setQueries(List.of("q1"));

            SerpQueryCursor cursor = cursor("q1", 1, 5);

            when(leadFinderProperties.getDiscovery()).thenReturn(discovery);
            when(queryScheduler.pickNextNonExhaustedQuery(List.of("q1")))
                    .thenReturn(Optional.of(new DiscoveryQueryScheduler.QueryPick(0, "q1", cursor)));
            when(queryScheduler.isExhausted(cursor)).thenReturn(false);
            when(serpApiService.searchUrls(anyString(), anyInt(), anyInt()))
                    .thenReturn(List.of("https://farm.example.com"));

            when(urlNormalizer.isNotFileUrl("https://farm.example.com")).thenReturn(true);
            when(discoveryUrlFilter.isAllowedDomain("https://farm.example.com")).thenReturn(true);
            when(urlNormalizer.normalizeUrl("https://farm.example.com")).thenReturn("https://farm.example.com");
            when(duplicateChecker.checkAlreadySeen("https://farm.example.com"))
                    .thenReturn(DiscoveryDuplicateChecker.SeenDecision.NOT_SEEN);
            when(discoveryUrlFilter.isHardNegativePath("https://farm.example.com")).thenReturn(false);
            when(urlScorer.computeDomainPriorityScore("https://farm.example.com")).thenReturn(42);
            when(snippetFetcher.fetchTextSnippet("https://farm.example.com"))
                    .thenReturn("Familienbetrieb Spargel und Erdbeeren mit Direktvermarktung und Hofladen.");
            when(farmClassifier.classifyFarm(anyString(), anyString()))
                    .thenReturn(new FarmClassificationResult(
                            true,
                            false,
                            "farm",
                            "https://farm.example.com/kontakt"
                    ));

            List<String> result = discoveryService.findCandidateFarmUrls(5);

            assertThat(result).containsExactlyInAnyOrder(
                    "https://farm.example.com",
                    "https://farm.example.com/kontakt"
            );

            verify(farmClassifier, times(1)).classifyFarm(
                    "https://farm.example.com",
                    "Familienbetrieb Spargel und Erdbeeren mit Direktvermarktung und Hofladen."
            );
            verify(discoveredUrlWriter, times(1)).save(
                    "https://farm.example.com",
                    new FarmClassificationResult(true, false, "farm", "https://farm.example.com/kontakt")
            );
            verify(discoveryRunStatsWriter, times(1)).save(
                    anyString(),
                    any(LocalDateTime.class),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt()
            );
        }

        @Test
        @DisplayName("should reject candidate when classifier marks not farm")
        void shouldRejectCandidateWhenClassifierMarksNotFarm() {
            LeadFinderProperties.Discovery discovery = baseDiscoveryConfig();
            discovery.setQueries(List.of("q1"));

            SerpQueryCursor cursor = cursor("q1", 1, 5);

            when(leadFinderProperties.getDiscovery()).thenReturn(discovery);
            when(queryScheduler.pickNextNonExhaustedQuery(List.of("q1")))
                    .thenReturn(Optional.of(new DiscoveryQueryScheduler.QueryPick(0, "q1", cursor)));
            when(queryScheduler.isExhausted(cursor)).thenReturn(false);
            when(serpApiService.searchUrls(anyString(), anyInt(), anyInt()))
                    .thenReturn(List.of("https://notfarm.example.com"));

            when(urlNormalizer.isNotFileUrl("https://notfarm.example.com")).thenReturn(true);
            when(discoveryUrlFilter.isAllowedDomain("https://notfarm.example.com")).thenReturn(true);
            when(urlNormalizer.normalizeUrl("https://notfarm.example.com")).thenReturn("https://notfarm.example.com");
            when(duplicateChecker.checkAlreadySeen("https://notfarm.example.com"))
                    .thenReturn(DiscoveryDuplicateChecker.SeenDecision.NOT_SEEN);
            when(discoveryUrlFilter.isHardNegativePath("https://notfarm.example.com")).thenReturn(false);
            when(urlScorer.computeDomainPriorityScore("https://notfarm.example.com")).thenReturn(10);
            when(snippetFetcher.fetchTextSnippet("https://notfarm.example.com"))
                    .thenReturn("Portal branżowy i katalog dostawców.");
            when(farmClassifier.classifyFarm(anyString(), anyString()))
                    .thenReturn(new FarmClassificationResult(false, false, "not farm", null));

            List<String> result = discoveryService.findCandidateFarmUrls(5);

            assertThat(result).isEmpty();
            verify(discoveredUrlWriter, times(1)).save(
                    anyString(),
                    any(FarmClassificationResult.class)
            );

            ArgumentCaptor<Integer> rejectedCaptor = ArgumentCaptor.forClass(Integer.class);
            verify(discoveryRunStatsWriter).save(
                    anyString(),
                    any(LocalDateTime.class),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    rejectedCaptor.capture(),
                    anyInt(),
                    anyInt()
            );
            assertThat(rejectedCaptor.getValue()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("should skip candidate when snippet is blank")
        void shouldSkipCandidateWhenSnippetIsBlank() {
            LeadFinderProperties.Discovery discovery = baseDiscoveryConfig();
            discovery.setQueries(List.of("q1"));

            SerpQueryCursor cursor = cursor("q1", 1, 5);

            when(leadFinderProperties.getDiscovery()).thenReturn(discovery);
            when(queryScheduler.pickNextNonExhaustedQuery(List.of("q1")))
                    .thenReturn(Optional.of(new DiscoveryQueryScheduler.QueryPick(0, "q1", cursor)));
            when(queryScheduler.isExhausted(cursor)).thenReturn(false);
            when(serpApiService.searchUrls(anyString(), anyInt(), anyInt()))
                    .thenReturn(List.of("https://farm.example.com"));

            when(urlNormalizer.isNotFileUrl("https://farm.example.com")).thenReturn(true);
            when(discoveryUrlFilter.isAllowedDomain("https://farm.example.com")).thenReturn(true);
            when(urlNormalizer.normalizeUrl("https://farm.example.com")).thenReturn("https://farm.example.com");
            when(duplicateChecker.checkAlreadySeen("https://farm.example.com"))
                    .thenReturn(DiscoveryDuplicateChecker.SeenDecision.NOT_SEEN);
            when(discoveryUrlFilter.isHardNegativePath("https://farm.example.com")).thenReturn(false);
            when(urlScorer.computeDomainPriorityScore("https://farm.example.com")).thenReturn(20);
            when(snippetFetcher.fetchTextSnippet("https://farm.example.com")).thenReturn("   ");

            List<String> result = discoveryService.findCandidateFarmUrls(5);

            assertThat(result).isEmpty();
            verify(farmClassifier, never()).classifyFarm(anyString(), anyString());
            verify(discoveredUrlWriter, never()).save(anyString(), any(FarmClassificationResult.class));
        }

        @Test
        @DisplayName("should skip already seen urls before classification")
        void shouldSkipAlreadySeenUrlsBeforeClassification() {
            LeadFinderProperties.Discovery discovery = baseDiscoveryConfig();
            discovery.setQueries(List.of("q1"));

            SerpQueryCursor cursor = cursor("q1", 1, 5);

            when(leadFinderProperties.getDiscovery()).thenReturn(discovery);
            when(queryScheduler.pickNextNonExhaustedQuery(List.of("q1")))
                    .thenReturn(Optional.of(new DiscoveryQueryScheduler.QueryPick(0, "q1", cursor)));
            when(queryScheduler.isExhausted(cursor)).thenReturn(false);
            when(serpApiService.searchUrls(anyString(), anyInt(), anyInt()))
                    .thenReturn(List.of("https://farm.example.com"));

            when(urlNormalizer.isNotFileUrl("https://farm.example.com")).thenReturn(true);
            when(discoveryUrlFilter.isAllowedDomain("https://farm.example.com")).thenReturn(true);
            when(urlNormalizer.normalizeUrl("https://farm.example.com")).thenReturn("https://farm.example.com");
            when(duplicateChecker.checkAlreadySeen("https://farm.example.com"))
                    .thenReturn(DiscoveryDuplicateChecker.SeenDecision.SEEN_BY_URL);

            List<String> result = discoveryService.findCandidateFarmUrls(5);

            assertThat(result).isEmpty();
            verify(snippetFetcher, never()).fetchTextSnippet(anyString());
            verify(farmClassifier, never()).classifyFarm(anyString(), anyString());
            verify(discoveredUrlWriter, never()).save(anyString(), any(FarmClassificationResult.class));
        }

        @Test
        @DisplayName("should stop processing when limit is reached")
        void shouldStopProcessingWhenLimitIsReached() {
            LeadFinderProperties.Discovery discovery = baseDiscoveryConfig();
            discovery.setQueries(List.of("q1"));

            SerpQueryCursor cursor = cursor("q1", 1, 5);

            when(leadFinderProperties.getDiscovery()).thenReturn(discovery);
            when(queryScheduler.pickNextNonExhaustedQuery(List.of("q1")))
                    .thenReturn(Optional.of(new DiscoveryQueryScheduler.QueryPick(0, "q1", cursor)));
            when(queryScheduler.isExhausted(cursor)).thenReturn(false);
            when(serpApiService.searchUrls(anyString(), anyInt(), anyInt()))
                    .thenReturn(List.of("https://a.example.com", "https://b.example.com"));

            when(urlNormalizer.isNotFileUrl(anyString())).thenReturn(true);
            when(discoveryUrlFilter.isAllowedDomain(anyString())).thenReturn(true);
            when(urlNormalizer.normalizeUrl(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
            when(duplicateChecker.checkAlreadySeen(anyString()))
                    .thenReturn(DiscoveryDuplicateChecker.SeenDecision.NOT_SEEN);
            when(discoveryUrlFilter.isHardNegativePath(anyString())).thenReturn(false);
            when(urlScorer.computeDomainPriorityScore(anyString())).thenReturn(10);
            when(snippetFetcher.fetchTextSnippet(anyString()))
                    .thenReturn("Familienbetrieb mit Anbau und Hofverkauf.");
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
            when(queryScheduler.pickNextNonExhaustedQuery(List.of("q1")))
                    .thenReturn(Optional.of(new DiscoveryQueryScheduler.QueryPick(0, "q1", cursor)));
            when(queryScheduler.isExhausted(cursor)).thenReturn(false);
            when(serpApiService.searchUrls(anyString(), anyInt(), anyInt())).thenReturn(List.of());

            discoveryService.findCandidateFarmUrls(5);

            verify(queryScheduler, times(1)).saveCursorAfterRun(any(SerpQueryCursor.class), anyInt());
            verify(discoveryRunStatsWriter, times(1)).save(
                    anyString(),
                    any(LocalDateTime.class),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt()
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