package com.mike.leadfarmfinder.service.serpquery;

import com.mike.leadfarmfinder.config.LeadFinderProperties;
import com.mike.leadfarmfinder.entity.SerpQueryHistory;
import com.mike.leadfarmfinder.entity.SerpQueryOverride;
import com.mike.leadfarmfinder.repository.SerpQueryHistoryRepository;
import com.mike.leadfarmfinder.repository.SerpQueryOverrideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SerpQueryCycleService implements SerpQueryCyclePort {

    private final SerpQueryScoringPort scoringPort;
    private final SerpQueryGenerator queryGenerator;
    private final SerpQueryOverrideRepository overrideRepository;
    private final SerpQueryHistoryRepository historyRepository;
    private final LeadFinderProperties leadFinderProperties;

    @Override
    @Transactional
    public List<String> runCycle(List<String> queries) {
        int threshold = leadFinderProperties.getQueryCycle().getScoreThreshold();

        log.info("SerpQueryCycleService: starting cycle for {} queries, threshold={}", queries.size(), threshold);

        Set<String> existingQueries = buildExistingQueriesSet(queries);
        List<String> replaced = new ArrayList<>();
        int cycleIndex = 0;

        for (String query : queries) {
            int score = scoringPort.scoreAndSave(query);

            if (score == -1) {
                log.info("SerpQueryCycleService: no data for query='{}', skipping", query);
                cycleIndex++;
                continue;
            }

            if (score >= threshold) {
                log.info("SerpQueryCycleService: query='{}' score={} OK", query, score);
                cycleIndex++;
                continue;
            }

            log.info("SerpQueryCycleService: query='{}' score={} below threshold={}, replacing",
                    query, score, threshold);

            String newQuery = queryGenerator.generate(cycleIndex, existingQueries);
            existingQueries.add(newQuery);

            saveOverride(query, newQuery, score);
            replaced.add(query);

            cycleIndex++;
        }

        log.info("SerpQueryCycleService: cycle finished, replaced {}/{} queries", replaced.size(), queries.size());
        return replaced;
    }

    private Set<String> buildExistingQueriesSet(List<String> yamlQueries) {
        Set<String> existing = new HashSet<>(yamlQueries);
        overrideRepository.findByActiveTrue()
                .forEach(o -> {
                    existing.add(o.getOriginalQuery());
                    existing.add(o.getOverrideQuery());
                });
        return existing;
    }

    private void saveOverride(String originalQuery, String newQuery, int oldScore) {
        overrideRepository.findByOriginalQueryAndActiveTrue(originalQuery)
                .ifPresent(existing -> {
                    existing.setActive(false);
                    overrideRepository.save(existing);
                    log.info("SerpQueryCycleService: deactivated old override for query='{}'", originalQuery);
                });

        SerpQueryOverride override = new SerpQueryOverride();
        override.setOriginalQuery(originalQuery);
        override.setOverrideQuery(newQuery);
        override.setOriginalScore(oldScore);
        override.setTestedScore(50);
        override.setActive(true);
        overrideRepository.save(override);

        SerpQueryHistory history = new SerpQueryHistory();
        history.setReplacedQuery(originalQuery);
        history.setNewQuery(newQuery);
        history.setReplacedQueryScore(oldScore);
        history.setNewQueryScore(50);
        historyRepository.save(history);

        log.info("SerpQueryCycleService: replaced query='{}' with='{}' (oldScore={})",
                originalQuery, newQuery, oldScore);
    }
}