package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.config.LeadFinderProperties;
import com.mike.leadfarmfinder.dto.FarmClassificationResult;
import com.mike.leadfarmfinder.entity.DiscoveryRunStats;
import com.mike.leadfarmfinder.entity.DiscoveredUrl;
import com.mike.leadfarmfinder.entity.SerpQueryCursor;
import com.mike.leadfarmfinder.repository.DiscoveredUrlRepository;
import com.mike.leadfarmfinder.repository.DiscoveryRunStatsRepository;
import com.mike.leadfarmfinder.repository.SerpQueryCursorRepository;
import com.mike.leadfarmfinder.service.discovery.DiscoveryUrlFilter;
import com.mike.leadfarmfinder.service.discovery.DiscoveryUrlNormalizer;
import com.mike.leadfarmfinder.service.discovery.DiscoveryUrlScorer;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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
    private SerpApiService serpApiService;
    @Mock
    private OpenAiFarmClassifier farmClassifier;
    @Mock
    private SerpQueryCursorRepository serpQueryCursorRepository;
    @Mock
    private DiscoveryRunStatsRepository discoveryRunStatsRepository;
    @Mock
    private DiscoveredUrlRepository discoveredUrlRepository;
    @Mock
    private LeadFinderProperties leadFinderProperties;

    private DiscoveryService discoveryService;

    @BeforeEach
    void setUp() {
        discoveryService = new DiscoveryService(
                urlNormalizer,
                discoveryUrlFilter,
                urlScorer,
                serpApiService,
                farmClassifier,
                serpQueryCursorRepository,
                discoveryRunStatsRepository,
                discoveredUrlRepository,
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

            when(serpQueryCursorRepository.findByQuery("q1"))
                    .thenReturn(Optional.of(cursor("q1", 6, 5)));
            when(serpQueryCursorRepository.findByQuery("q2"))
                    .thenReturn(Optional.of(cursor("q2", 7, 5)));

            List<String> result = discoveryService.findCandidateFarmUrls(5);

            assertThat(result).isEmpty();
            verify(serpApiService, never()).searchUrls(anyString(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("should mark query done when SERP page is empty and threshold is reached")
        void shouldMarkQueryDoneWhenSerpPageIsEmptyAndThresholdIsReached() {
            LeadFinderProperties.Discovery discovery = baseDiscoveryConfig();
            discovery.setQueries(List.of("q1"));
            when(leadFinderProperties.getDiscovery()).thenReturn(discovery);

            SerpQueryCursor c = cursor("q1", 1, 3);
            when(serpQueryCursorRepository.findByQuery("q1")).thenReturn(Optional.of(c));
            when(serpApiService.searchUrls(anyString(), anyInt(), anyInt())).thenReturn(List.of());

            List<String> result = discoveryService.findCandidateFarmUrls(5);

            assertThat(result).isEmpty();
            assertThat(c.getCurrentPage()).isEqualTo(4);

            ArgumentCaptor<SerpQueryCursor> cursorCaptor = ArgumentCaptor.forClass(SerpQueryCursor.class);
            verify(serpQueryCursorRepository).save(cursorCaptor.capture());
            assertThat(cursorCaptor.getValue().getCurrentPage()).isEqualTo(4);

            verify(discoveryRunStatsRepository).save(any(DiscoveryRunStats.class));
        }

        @Test
        @DisplayName("should return accepted URLs when classifier marks farm")
        void shouldReturnAcceptedUrlsWhenClassifierMarksFarm() throws Exception {
            LeadFinderProperties.Discovery discovery = baseDiscoveryConfig();
            discovery.setQueries(List.of("q1"));
            when(leadFinderProperties.getDiscovery()).thenReturn(discovery);

            SerpQueryCursor c = cursor("q1", 1, 5);
            when(serpQueryCursorRepository.findByQuery("q1")).thenReturn(Optional.of(c));
            when(serpApiService.searchUrls(anyString(), anyInt(), anyInt()))
                    .thenReturn(List.of("https://farm.example.com"));

            when(urlNormalizer.isNotFileUrl(anyString())).thenReturn(true);
            when(discoveryUrlFilter.isAllowedDomain(anyString())).thenReturn(true);
            when(urlNormalizer.normalizeUrl(anyString())).thenAnswer(inv -> inv.getArgument(0));
            when(discoveredUrlRepository.existsByUrl(anyString())).thenReturn(false);
            when(discoveryUrlFilter.isHardNegativePath(anyString())).thenReturn(false);
            when(urlScorer.computeDomainPriorityScore(anyString())).thenReturn(42);
            when(farmClassifier.classifyFarm(anyString(), anyString()))
                    .thenReturn(new FarmClassificationResult(true, false, "farm", "https://farm.example.com/kontakt"));
            when(discoveredUrlRepository.findByUrl(anyString())).thenReturn(Optional.empty());
            when(urlNormalizer.extractNormalizedDomain(anyString())).thenReturn("farm.example.com");
            when(discoveredUrlRepository.save(any(DiscoveredUrl.class))).thenAnswer(inv -> inv.getArgument(0));

            try (MockedStatic<Jsoup> jsoup = org.mockito.Mockito.mockStatic(Jsoup.class)) {
                Connection connection = mock(Connection.class);
                Document document = mock(Document.class);
                when(document.text()).thenReturn("x".repeat(300));
                when(document.select(anyString())).thenReturn(mock(org.jsoup.select.Elements.class));

                jsoup.when(() -> Jsoup.connect(anyString())).thenReturn(connection);
                when(connection.userAgent(anyString())).thenReturn(connection);
                when(connection.timeout(anyInt())).thenReturn(connection);
                when(connection.followRedirects(anyBoolean())).thenReturn(connection);
                when(connection.get()).thenReturn(document);

                List<String> result = discoveryService.findCandidateFarmUrls(2);
                assertThat(result).containsExactlyInAnyOrder(
                        "https://farm.example.com",
                        "https://farm.example.com/kontakt"
                );
            }
        }

        @Test
        @DisplayName("should reject candidate when classifier marks not farm")
        void shouldRejectCandidateWhenClassifierMarksNotFarm() throws Exception {
            LeadFinderProperties.Discovery discovery = baseDiscoveryConfig();
            discovery.setQueries(List.of("q1"));
            when(leadFinderProperties.getDiscovery()).thenReturn(discovery);

            SerpQueryCursor c = cursor("q1", 1, 5);
            when(serpQueryCursorRepository.findByQuery("q1")).thenReturn(Optional.of(c));
            when(serpApiService.searchUrls(anyString(), anyInt(), anyInt()))
                    .thenReturn(List.of("https://notfarm.example.com"));

            when(urlNormalizer.isNotFileUrl(anyString())).thenReturn(true);
            when(discoveryUrlFilter.isAllowedDomain(anyString())).thenReturn(true);
            when(urlNormalizer.normalizeUrl(anyString())).thenAnswer(inv -> inv.getArgument(0));
            when(discoveredUrlRepository.existsByUrl(anyString())).thenReturn(false);
            when(discoveryUrlFilter.isHardNegativePath(anyString())).thenReturn(false);
            when(urlScorer.computeDomainPriorityScore(anyString())).thenReturn(10);
            when(farmClassifier.classifyFarm(anyString(), anyString()))
                    .thenReturn(new FarmClassificationResult(false, false, "not farm", null));
            when(discoveredUrlRepository.findByUrl(anyString())).thenReturn(Optional.empty());
            when(urlNormalizer.extractNormalizedDomain(anyString())).thenReturn("notfarm.example.com");
            when(discoveredUrlRepository.save(any(DiscoveredUrl.class))).thenAnswer(inv -> inv.getArgument(0));

            try (MockedStatic<Jsoup> jsoup = org.mockito.Mockito.mockStatic(Jsoup.class)) {
                Connection connection = mock(Connection.class);
                Document document = mock(Document.class);
                when(document.text()).thenReturn("x".repeat(300));
                when(document.select(anyString())).thenReturn(mock(org.jsoup.select.Elements.class));

                jsoup.when(() -> Jsoup.connect(anyString())).thenReturn(connection);
                when(connection.userAgent(anyString())).thenReturn(connection);
                when(connection.timeout(anyInt())).thenReturn(connection);
                when(connection.followRedirects(anyBoolean())).thenReturn(connection);
                when(connection.get()).thenReturn(document);

                List<String> result = discoveryService.findCandidateFarmUrls(5);
                assertThat(result).isEmpty();
            }

            ArgumentCaptor<DiscoveryRunStats> statsCaptor = ArgumentCaptor.forClass(DiscoveryRunStats.class);
            verify(discoveryRunStatsRepository).save(statsCaptor.capture());
            assertThat(statsCaptor.getValue().getRejectedUrls()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("should stop processing when limit is reached")
        void shouldStopProcessingWhenLimitIsReached() throws Exception {
            LeadFinderProperties.Discovery discovery = baseDiscoveryConfig();
            discovery.setQueries(List.of("q1"));
            when(leadFinderProperties.getDiscovery()).thenReturn(discovery);

            SerpQueryCursor c = cursor("q1", 1, 5);
            when(serpQueryCursorRepository.findByQuery("q1")).thenReturn(Optional.of(c));
            when(serpApiService.searchUrls(anyString(), anyInt(), anyInt()))
                    .thenReturn(List.of("https://a.example.com", "https://b.example.com"));

            when(urlNormalizer.isNotFileUrl(anyString())).thenReturn(true);
            when(discoveryUrlFilter.isAllowedDomain(anyString())).thenReturn(true);
            when(urlNormalizer.normalizeUrl(anyString())).thenAnswer(inv -> inv.getArgument(0));
            when(discoveredUrlRepository.existsByUrl(anyString())).thenReturn(false);
            when(discoveryUrlFilter.isHardNegativePath(anyString())).thenReturn(false);
            when(urlScorer.computeDomainPriorityScore(anyString())).thenReturn(10);
            when(farmClassifier.classifyFarm(anyString(), anyString()))
                    .thenReturn(new FarmClassificationResult(true, false, "farm", null));
            when(discoveredUrlRepository.findByUrl(anyString())).thenReturn(Optional.empty());
            when(urlNormalizer.extractNormalizedDomain(anyString())).thenReturn("example.com");
            when(discoveredUrlRepository.save(any(DiscoveredUrl.class))).thenAnswer(inv -> inv.getArgument(0));

            try (MockedStatic<Jsoup> jsoup = org.mockito.Mockito.mockStatic(Jsoup.class)) {
                Connection connection = mock(Connection.class);
                Document document = mock(Document.class);
                when(document.text()).thenReturn("x".repeat(300));
                when(document.select(anyString())).thenReturn(mock(org.jsoup.select.Elements.class));

                jsoup.when(() -> Jsoup.connect(anyString())).thenReturn(connection);
                when(connection.userAgent(anyString())).thenReturn(connection);
                when(connection.timeout(anyInt())).thenReturn(connection);
                when(connection.followRedirects(anyBoolean())).thenReturn(connection);
                when(connection.get()).thenReturn(document);

                List<String> result = discoveryService.findCandidateFarmUrls(1);
                assertThat(result).hasSize(1);
            }

            verify(farmClassifier, times(1)).classifyFarm(anyString(), anyString());
        }

        @Test
        @DisplayName("should save cursor and discovery run stats after run")
        void shouldSaveCursorAndDiscoveryRunStatsAfterRun() {
            LeadFinderProperties.Discovery discovery = baseDiscoveryConfig();
            discovery.setQueries(List.of("q1"));
            when(leadFinderProperties.getDiscovery()).thenReturn(discovery);

            SerpQueryCursor c = cursor("q1", 1, 3);
            when(serpQueryCursorRepository.findByQuery("q1")).thenReturn(Optional.of(c));
            when(serpApiService.searchUrls(anyString(), anyInt(), anyInt())).thenReturn(List.of());

            discoveryService.findCandidateFarmUrls(5);

            verify(serpQueryCursorRepository, times(1)).save(any(SerpQueryCursor.class));
            verify(discoveryRunStatsRepository, times(1)).save(any(DiscoveryRunStats.class));
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
        SerpQueryCursor c = new SerpQueryCursor();
        c.setQuery(query);
        c.setCurrentPage(currentPage);
        c.setMaxPage(maxPage);
        return c;
    }
}
