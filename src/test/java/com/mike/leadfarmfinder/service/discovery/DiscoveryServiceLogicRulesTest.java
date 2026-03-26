package com.mike.leadfarmfinder.service.discovery;

import com.mike.leadfarmfinder.config.LeadFinderProperties;
import com.mike.leadfarmfinder.dto.FarmClassificationResult;
import com.mike.leadfarmfinder.service.DiscoveryService;
import com.mike.leadfarmfinder.service.OpenAiFarmClassifier;
import com.mike.leadfarmfinder.service.SerpApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscoveryServiceLogicRulesTest {

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
    @DisplayName("Query negatives rules")
    class QueryNegativesRulesTests {

        @Test
        @DisplayName("should append negatives when query does not contain them")
        void shouldAppendNegativesWhenQueryDoesNotContainThem() throws Exception {
            String result = invokePrivate(
                    "withQueryNegatives",
                    new Class[]{String.class},
                    "Saisonarbeit Erdbeeren Hof Niedersachsen"
            );

            assertThat(result)
                    .contains("-branchenbuch")
                    .contains("-facebook")
                    .contains("-booking")
                    .startsWith("Saisonarbeit Erdbeeren Hof Niedersachsen");
        }

        @Test
        @DisplayName("should not append negatives when query already contains core negative")
        void shouldNotAppendNegativesWhenQueryAlreadyContainsCoreNegative() throws Exception {
            String input = "Spargelhof Bayern -branchenbuch";

            String result = invokePrivate(
                    "withQueryNegatives",
                    new Class[]{String.class},
                    input
            );

            assertThat(result).isEqualTo(input);
        }
    }

    @Nested
    @DisplayName("Paging and exhaustion rules")
    class PagingAndExhaustionRulesTests {

        @Test
        @DisplayName("should handle empty SERP page and mark query done immediately")
        void shouldHandleEmptySerpPageAndMarkQueryDoneImmediately() throws Exception {

            Object outcome = invokePrivate(
                    "handleEmptySerpPage",
                    new Class[]{String.class, int.class, int.class, int.class},
                    "query",
                    1,
                    3,
                    0
            );

            int currentPage = invokeRecordAccessor(outcome, "currentPage", Integer.class);
            int streak = invokeRecordAccessor(outcome, "consecutiveEmptySerpPages", Integer.class);

            assertThat(currentPage).isEqualTo(4);
            assertThat(streak).isEqualTo(1);
        }

        @Test
        @DisplayName("should continue to next page when first empty new candidates page appears")
        void shouldContinueToNextPageWhenFirstEmptyNewCandidatesPageAppears() throws Exception {
            when(queryScheduler.advancePageOrExhaust(2, 5)).thenReturn(3);

            Object outcome = invokePrivate(
                    "handleEmptyNewCandidates",
                    new Class[]{List.class, String.class, int.class, int.class, int.class, int.class},
                    List.of(),
                    "query",
                    2,
                    5,
                    0,
                    0
            );

            boolean shouldContinue = invokeRecordAccessor(outcome, "shouldContinue", Boolean.class);
            boolean shouldBreak = invokeRecordAccessor(outcome, "shouldBreak", Boolean.class);
            int nextPage = invokeRecordAccessor(outcome, "currentPage", Integer.class);
            int newStreak = invokeRecordAccessor(outcome, "consecutiveEmptyNewUrls", Integer.class);

            assertThat(shouldContinue).isTrue();
            assertThat(shouldBreak).isFalse();
            assertThat(nextPage).isEqualTo(3);
            assertThat(newStreak).isEqualTo(1);
        }

        @Test
        @DisplayName("should mark query done after second consecutive empty new candidates page")
        void shouldMarkQueryDoneAfterSecondConsecutiveEmptyNewCandidatesPage() throws Exception {
            Object outcome = invokePrivate(
                    "handleEmptyNewCandidates",
                    new Class[]{List.class, String.class, int.class, int.class, int.class, int.class},
                    List.of(),
                    "query",
                    2,
                    5,
                    1,
                    0
            );

            boolean shouldContinue = invokeRecordAccessor(outcome, "shouldContinue", Boolean.class);
            boolean shouldBreak = invokeRecordAccessor(outcome, "shouldBreak", Boolean.class);
            int nextPage = invokeRecordAccessor(outcome, "currentPage", Integer.class);
            int newStreak = invokeRecordAccessor(outcome, "consecutiveEmptyNewUrls", Integer.class);

            assertThat(shouldContinue).isFalse();
            assertThat(shouldBreak).isTrue();
            assertThat(nextPage).isEqualTo(6);
            assertThat(newStreak).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Selection and scoring rules")
    class SelectionAndScoringRulesTests {

        @Test
        @DisplayName("should select only new urls and update counters")
        void shouldSelectOnlyNewUrlsAndUpdateCounters() throws Exception {
            List<String> cleaned = List.of(
                    "https://seen.example.com",
                    "https://new.example.com",
                    "https://new.example.com",
                    "https://hard.example.com/blog"
            );

            when(urlNormalizer.normalizeUrl(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
            when(duplicateChecker.checkAlreadySeen("https://seen.example.com"))
                    .thenReturn(DiscoveryDuplicateChecker.SeenDecision.SEEN_BY_URL);
            when(duplicateChecker.checkAlreadySeen("https://new.example.com"))
                    .thenReturn(DiscoveryDuplicateChecker.SeenDecision.NOT_SEEN);
            when(duplicateChecker.checkAlreadySeen("https://hard.example.com/blog"))
                    .thenReturn(DiscoveryDuplicateChecker.SeenDecision.NOT_SEEN);

            when(discoveryUrlFilter.isHardNegativePath("https://new.example.com")).thenReturn(false);
            when(discoveryUrlFilter.isHardNegativePath("https://hard.example.com/blog")).thenReturn(true);

            Object outcome = invokePrivate(
                    "selectNewUrlsForClassification",
                    new Class[]{List.class, int.class, int.class},
                    cleaned,
                    0,
                    10
            );

            @SuppressWarnings("unchecked")
            List<String> newUrlsOnly = invokeRecordAccessor(outcome, "newUrlsOnly", List.class);
            int filteredAlready = invokeRecordAccessor(outcome, "filteredAlreadyDiscoveredDelta", Integer.class);
            int alreadySkipped = invokeRecordAccessor(outcome, "alreadySeenSkippedDelta", Integer.class);
            int rejected = invokeRecordAccessor(outcome, "rejectedDelta", Integer.class);

            assertThat(newUrlsOnly).containsExactly("https://new.example.com");
            assertThat(filteredAlready).isEqualTo(1);
            assertThat(alreadySkipped).isEqualTo(1);
            assertThat(rejected).isEqualTo(1);
        }

        @Test
        @DisplayName("should count normalized changed when normalizer changes url")
        void shouldCountNormalizedChangedWhenNormalizerChangesUrl() throws Exception {
            List<String> cleaned = List.of("https://example.com/");

            when(urlNormalizer.normalizeUrl("https://example.com/")).thenReturn("https://example.com");
            when(duplicateChecker.checkAlreadySeen("https://example.com"))
                    .thenReturn(DiscoveryDuplicateChecker.SeenDecision.NOT_SEEN);
            when(discoveryUrlFilter.isHardNegativePath("https://example.com")).thenReturn(false);

            Object outcome = invokePrivate(
                    "selectNewUrlsForClassification",
                    new Class[]{List.class, int.class, int.class},
                    cleaned,
                    0,
                    10
            );

            int normalizedChanged = invokeRecordAccessor(outcome, "normalizedChangedDelta", Integer.class);

            @SuppressWarnings("unchecked")
            List<String> newUrlsOnly = invokeRecordAccessor(outcome, "newUrlsOnly", List.class);

            assertThat(normalizedChanged).isEqualTo(1);
            assertThat(newUrlsOnly).containsExactly("https://example.com");
        }

        @Test
        @DisplayName("should score urls and sort by descending priority")
        void shouldScoreUrlsAndSortByDescendingPriority() throws Exception {
            when(urlScorer.computeDomainPriorityScore("https://a.example.com")).thenReturn(10);
            when(urlScorer.computeDomainPriorityScore("https://b.example.com")).thenReturn(50);
            when(urlScorer.computeDomainPriorityScore("https://c.example.com")).thenReturn(30);

            List<?> scored = invokePrivate(
                    "scoreNewUrls",
                    new Class[]{List.class},
                    List.of("https://a.example.com", "https://b.example.com", "https://c.example.com")
            );

            String firstUrl = invokeRecordAccessor(scored.get(0), "url", String.class);
            Integer firstScore = invokeRecordAccessor(scored.get(0), "score", Integer.class);
            String secondUrl = invokeRecordAccessor(scored.get(1), "url", String.class);

            assertThat(firstUrl).isEqualTo("https://b.example.com");
            assertThat(firstScore).isEqualTo(50);
            assertThat(secondUrl).isEqualTo("https://c.example.com");
        }
    }

    @Nested
    @DisplayName("Classification outcome rules")
    class ClassificationOutcomeRulesTests {

        @Test
        @DisplayName("should add source and contact url when farm is detected")
        void shouldAddSourceAndContactUrlWhenFarmIsDetected() throws Exception {
            Object scoredUrl = newScoredUrl("https://farm.example.com", 99);
            FarmClassificationResult result = new FarmClassificationResult(
                    true,
                    false,
                    "farm",
                    "https://farm.example.com/kontakt"
            );
            List<String> accepted = new ArrayList<>();

            Object outcome = invokePrivate(
                    "handleClassificationResult",
                    new Class[]{scoredUrl.getClass(), FarmClassificationResult.class, List.class},
                    scoredUrl,
                    result,
                    accepted
            );

            int rejected = invokeRecordAccessor(outcome, "rejectedDelta", Integer.class);

            assertThat(accepted).containsExactly(
                    "https://farm.example.com",
                    "https://farm.example.com/kontakt"
            );
            assertThat(rejected).isZero();
        }

        @Test
        @DisplayName("should increase rejected count when not farm is detected")
        void shouldIncreaseRejectedCountWhenNotFarmIsDetected() throws Exception {
            Object scoredUrl = newScoredUrl("https://notfarm.example.com", 12);
            FarmClassificationResult result = new FarmClassificationResult(
                    false,
                    false,
                    "not-farm",
                    null
            );
            List<String> accepted = new ArrayList<>();

            Object outcome = invokePrivate(
                    "handleClassificationResult",
                    new Class[]{scoredUrl.getClass(), FarmClassificationResult.class, List.class},
                    scoredUrl,
                    result,
                    accepted
            );

            int rejected = invokeRecordAccessor(outcome, "rejectedDelta", Integer.class);

            assertThat(accepted).isEmpty();
            assertThat(rejected).isEqualTo(1);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T invokePrivate(String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = DiscoveryService.class.getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        return (T) method.invoke(discoveryService, args);
    }

    @SuppressWarnings("unchecked")
    private <T> T invokeRecordAccessor(Object target, String accessor, Class<T> type) throws Exception {
        Method method = target.getClass().getDeclaredMethod(accessor);
        method.setAccessible(true);
        return (T) method.invoke(target);
    }

    private Object newScoredUrl(String url, int score) throws Exception {
        Class<?> scoredUrlClass = Class.forName("com.mike.leadfarmfinder.service.DiscoveryService$ScoredUrl");
        Constructor<?> constructor = scoredUrlClass.getDeclaredConstructor(String.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(url, score);
    }
}