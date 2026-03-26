package com.mike.leadfarmfinder.service.discovery;

import com.mike.leadfarmfinder.config.LeadFinderProperties;
import com.mike.leadfarmfinder.entity.SerpQueryCursor;
import com.mike.leadfarmfinder.repository.SerpQueryCursorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DiscoveryQueryScheduler {

    private final SerpQueryCursorRepository serpQueryCursorRepository;
    private final LeadFinderProperties leadFinderProperties;

    private int queryIndex = 0;

    public Optional<QueryPick> pickNextNonExhaustedQuery(List<String> queries) {
        for (int attempts = 0; attempts < queries.size(); attempts++) {
            int idx = queryIndex;
            String query = queries.get(idx);
            queryIndex = (queryIndex + 1) % queries.size();

            SerpQueryCursor cursor = loadOrCreateCursor(query);
            if (!isExhausted(cursor)) {
                return Optional.of(new QueryPick(idx, query, cursor));
            }
        }
        return Optional.empty();
    }

    public boolean isExhausted(SerpQueryCursor cursor) {
        return cursor.getCurrentPage() > cursor.getMaxPage();
    }

    public int advancePageOrExhaust(int currentPage, int maxPage) {
        int next = currentPage + 1;
        if (next > maxPage) {
            return maxPage + 1;
        }
        return next;
    }

    public void saveCursorAfterRun(SerpQueryCursor cursor, int currentPage) {
        cursor.setCurrentPage(currentPage);
        cursor.setLastRunAt(LocalDateTime.now());
        serpQueryCursorRepository.save(cursor);
    }

    private SerpQueryCursor loadOrCreateCursor(String query) {
        return serpQueryCursorRepository.findByQuery(query)
                .orElseGet(() -> {
                    SerpQueryCursor cursor = new SerpQueryCursor();
                    cursor.setQuery(query);
                    cursor.setCurrentPage(1);
                    cursor.setMaxPage(leadFinderProperties.getDiscovery().getDefaultMaxSerpPage());
                    cursor.setLastRunAt(null);

                    SerpQueryCursor saved = serpQueryCursorRepository.save(cursor);
                    log.info("DiscoveryQueryScheduler: created new SERP cursor for query='{}'", query);
                    return saved;
                });
    }

    public record QueryPick(int index, String query, SerpQueryCursor cursor) {
    }
}