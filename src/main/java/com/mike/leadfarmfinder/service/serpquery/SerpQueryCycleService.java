package com.mike.leadfarmfinder.service.serpquery;

import com.mike.leadfarmfinder.entity.SerpQueryHistory;
import com.mike.leadfarmfinder.entity.SerpQueryOverride;
import com.mike.leadfarmfinder.repository.SerpQueryHistoryRepository;
import com.mike.leadfarmfinder.repository.SerpQueryOverrideRepository;
import com.mike.leadfarmfinder.service.SerpApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SerpQueryCycleService implements SerpQueryCyclePort {

    private final SerpQueryScoringPort scoringPort;
    private final SerpQueryImproverPort improverPort;
    private final SerpApiService serpApiService;
    private final SerpQueryOverrideRepository overrideRepository;
    private final SerpQueryHistoryRepository historyRepository;

    @Value("${leadfinder.query-cycle.score-threshold:40}")
    private int scoreThreshold;

    @Value("${leadfinder.query-cycle.test-limit:5}")
    private int testLimit;

    @Override
    @Transactional
    public List<String> runCycle(List<String> queries) {
        List<String> replaced = new ArrayList<>();

        log.info("SerpQueryCycleService: starting cycle for {} queries, threshold={}", queries.size(), scoreThreshold);

        for (String query : queries) {
            try {
                int score = scoringPort.scoreAndSave(query);

                if (score == -1) {
                    log.debug("SerpQueryCycleService: no data for query='{}', skipping", query);
                    continue;
                }

                if (score >= scoreThreshold) {
                    log.debug("SerpQueryCycleService: query='{}' score={} is above threshold, skipping", query, score);
                    continue;
                }

                log.info("SerpQueryCycleService: query='{}' score={} is below threshold={}, improving",
                        query, score, scoreThreshold);

                List<String> suggestions = improverPort.suggestImprovements(query, score);
                if (suggestions.isEmpty()) {
                    log.warn("SerpQueryCycleService: no suggestions from agent for query='{}'", query);
                    continue;
                }

                String bestSuggestion = pickBestSuggestion(suggestions);
                if (bestSuggestion == null) {
                    log.warn("SerpQueryCycleService: all suggestions returned 0 SERP results for query='{}'", query);
                    continue;
                }

                int testedScore = scoreSuggestion(bestSuggestion);
                saveOverride(query, bestSuggestion, score, testedScore);
                saveHistory(query, bestSuggestion, score, testedScore);

                replaced.add(query);
                log.info("SerpQueryCycleService: replaced query='{}' with='{}' (oldScore={}, testedScore={})",
                        query, bestSuggestion, score, testedScore);

            } catch (Exception e) {
                log.error("SerpQueryCycleService: error processing query='{}', skipping", query, e);
            }
        }

        log.info("SerpQueryCycleService: cycle finished, replaced {}/{} queries", replaced.size(), queries.size());
        return replaced;
    }

    private String pickBestSuggestion(List<String> suggestions) {
        String best = null;
        int bestCount = 0;

        for (String suggestion : suggestions) {
            List<String> urls = serpApiService.searchUrls(suggestion, testLimit);
            log.info("SerpQueryCycleService: suggestion='{}' returned {} urls", suggestion, urls.size());

            if (urls.size() > bestCount) {
                bestCount = urls.size();
                best = suggestion;
            }
        }

        return bestCount > 0 ? best : null;
    }

    private int scoreSuggestion(String suggestion) {
        List<String> urls = serpApiService.searchUrls(suggestion, 10);
        if (urls.isEmpty()) {
            return 0;
        }
        // Prosty score na podstawie liczby wyników — pełna ocena przyjdzie po real runach
        return Math.min(100, urls.size() * 10);
    }

    private void saveOverride(String originalQuery, String overrideQuery, int originalScore, int testedScore) {
        // Deaktywuj poprzedni aktywny override dla tego query
        overrideRepository.findByOriginalQueryAndActiveTrue(originalQuery)
                .ifPresent(existing -> {
                    existing.setActive(false);
                    overrideRepository.save(existing);
                });

        SerpQueryOverride override = new SerpQueryOverride();
        override.setOriginalQuery(originalQuery);
        override.setOverrideQuery(overrideQuery);
        override.setOriginalScore(originalScore);
        override.setTestedScore(testedScore);
        override.setActive(true);
        overrideRepository.save(override);
    }

    private void saveHistory(String replacedQuery, String newQuery, int replacedScore, int newScore) {
        SerpQueryHistory history = new SerpQueryHistory();
        history.setReplacedQuery(replacedQuery);
        history.setNewQuery(newQuery);
        history.setReplacedQueryScore(replacedScore);
        history.setNewQueryScore(newScore);
        history.setAiReason("AI-generated replacement via SerpQueryCycleService");
        historyRepository.save(history);
    }
}