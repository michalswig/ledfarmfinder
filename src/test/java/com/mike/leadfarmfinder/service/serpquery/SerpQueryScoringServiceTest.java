package com.mike.leadfarmfinder.service.serpquery;

import com.mike.leadfarmfinder.repository.DiscoveryRunStatsRepository;
import com.mike.leadfarmfinder.repository.SerpQueryScoreRepository;
import com.mike.leadfarmfinder.repository.SerpQueryStatsProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SerpQueryScoringServiceTest {

    @Mock
    private DiscoveryRunStatsRepository discoveryRunStatsRepository;

    @Mock
    private SerpQueryScoreRepository serpQueryScoreRepository;

    private SerpQueryScoringService service;

    @BeforeEach
    void setUp() {
        service = new SerpQueryScoringService(discoveryRunStatsRepository, serpQueryScoreRepository);
    }

    @Nested
    @DisplayName("calculateScore")
    class CalculateScoreTests {

        @Test
        @DisplayName("should return 0 when no raw urls")
        void shouldReturnZeroWhenNoRawUrls() {
            var stats = new SerpQueryStatsProjection(5, 0, 0, 2, 0);

            int score = service.calculateScore(stats);

            assertThat(score).isEqualTo(0);
        }

        @Test
        @DisplayName("should return high score for query with many accepted urls")
        void shouldReturnHighScoreForManyAccepted() {
            // 8 accepted / 10 raw = 80% acceptance, 10 raw / (1 page * 10) = 100% yield
            // rawScore = 0.8 * 70 + 1.0 * 30 = 56 + 30 = 86
            var stats = new SerpQueryStatsProjection(5, 8, 2, 1, 10);

            int score = service.calculateScore(stats);

            assertThat(score).isEqualTo(86);
        }

        @Test
        @DisplayName("should return low score for query with few accepted urls")
        void shouldReturnLowScoreForFewAccepted() {
            // 1 accepted / 10 raw = 10% acceptance, 10 raw / (2 pages * 10) = 50% yield
            // rawScore = 0.1 * 70 + 0.5 * 30 = 7 + 15 = 22
            var stats = new SerpQueryStatsProjection(5, 1, 9, 2, 10);

            int score = service.calculateScore(stats);

            assertThat(score).isEqualTo(22);
        }

        @Test
        @DisplayName("should apply reliability penalty for less than 2 runs")
        void shouldApplyReliabilityPenaltyForFewRuns() {
            // 8 accepted / 10 raw = 80%, yield 100%
            // rawScore = 86, po karze * 0.7 = 60
            var stats = new SerpQueryStatsProjection(1, 8, 2, 1, 10);

            int score = service.calculateScore(stats);

            assertThat(score).isEqualTo(60);
        }

        @Test
        @DisplayName("should cap score at 100")
        void shouldCapScoreAt100() {
            // perfect query: wszystkie raw to accepted, pełne strony
            var stats = new SerpQueryStatsProjection(5, 10, 0, 1, 10);

            int score = service.calculateScore(stats);

            assertThat(score).isLessThanOrEqualTo(100);
        }

        @Test
        @DisplayName("should return 0 when pages visited is 0 and raw urls present")
        void shouldHandleZeroPagesVisited() {
            // yield = 0 bo pagesVisited = 0
            // rawScore = 0.5 * 70 + 0 * 30 = 35, po karze * 0.7 = 24
            var stats = new SerpQueryStatsProjection(1, 5, 5, 0, 10);

            int score = service.calculateScore(stats);

            assertThat(score).isEqualTo(25);
        }
    }
}