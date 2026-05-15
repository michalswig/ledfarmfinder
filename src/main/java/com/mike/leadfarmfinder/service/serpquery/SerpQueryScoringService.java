package com.mike.leadfarmfinder.service.serpquery;

import com.mike.leadfarmfinder.entity.SerpQueryScore;
import com.mike.leadfarmfinder.repository.DiscoveryRunStatsRepository;
import com.mike.leadfarmfinder.repository.SerpQueryScoreRepository;
import com.mike.leadfarmfinder.repository.SerpQueryStatsProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SerpQueryScoringService implements SerpQueryScoringPort {

    private static final int NO_DATA_SCORE = -1;
    private static final int MIN_RUNS_FOR_RELIABLE_SCORE = 2;

    private final DiscoveryRunStatsRepository discoveryRunStatsRepository;
    private final SerpQueryScoreRepository serpQueryScoreRepository;

    @Override
    @Transactional
    public int scoreAndSave(String query) {
        SerpQueryStatsProjection stats = discoveryRunStatsRepository.aggregateByQuery(query);

        if (stats == null || stats.runsCount() == 0) {
            log.info("SerpQueryScoringService: no data for query='{}', skipping", query);
            return NO_DATA_SCORE;
        }

        int score = calculateScore(stats);

        SerpQueryScore entity = serpQueryScoreRepository.findByQuery(query)
                .orElseGet(() -> {
                    SerpQueryScore s = new SerpQueryScore();
                    s.setQuery(query);
                    return s;
                });

        entity.setScore(score);
        entity.setAcceptedUrls((int) stats.totalAccepted());
        entity.setRejectedUrls((int) stats.totalRejected());
        entity.setPagesVisited((int) stats.totalPagesVisited());
        entity.setRunsCount((int) stats.runsCount());
        entity.setLastEvaluatedAt(LocalDateTime.now());

        serpQueryScoreRepository.save(entity);

        log.info("SerpQueryScoringService: scored query='{}' -> score={} (runs={}, accepted={}, rejected={})",
                query, score, stats.runsCount(), stats.totalAccepted(), stats.totalRejected());

        return score;
    }

    /**
     * Wzór scoringu oparty na metrykach z leadfarmfinder_query_patterns.md sekcja 11.
     *
     * Składowe:
     * - acceptanceRate (accepted / rawUrls)  — główny sygnał jakości
     * - newUrlRate (rawUrls / (pages * 10))  — czy query nie jest nasycone
     * - kara za brak danych (mało runów)
     */
    int calculateScore(SerpQueryStatsProjection stats) {
        if (stats.totalRawUrls() == 0) {
            return 0;
        }

        // accepted / rawUrls — ile z pobranych URLi to farmy
        double acceptanceRate = (double) stats.totalAccepted() / stats.totalRawUrls();

        // rawUrls / (pagesVisited * 10) — czy SERP zwraca wyniki (10 = standardowa strona)
        double serpYieldRate = stats.totalPagesVisited() == 0
                ? 0
                : (double) stats.totalRawUrls() / (stats.totalPagesVisited() * 10.0);

        // Bazowy score: 70% waga na acceptance, 30% na yield
        double rawScore = (acceptanceRate * 70.0) + (serpYieldRate * 30.0);

        // Kara za mało runów — wynik jest mniej wiarygodny
        if (stats.runsCount() < MIN_RUNS_FOR_RELIABLE_SCORE) {
            rawScore *= 0.7;
        }

        return (int) Math.min(100, Math.max(0, Math.round(rawScore)));
    }
}