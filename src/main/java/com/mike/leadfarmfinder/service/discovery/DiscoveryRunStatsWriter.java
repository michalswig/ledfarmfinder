package com.mike.leadfarmfinder.service.discovery;

import com.mike.leadfarmfinder.entity.DiscoveryRunStats;
import com.mike.leadfarmfinder.repository.DiscoveryRunStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DiscoveryRunStatsWriter {

    private final DiscoveryRunStatsRepository discoveryRunStatsRepository;

    public void save(
            String query,
            LocalDateTime startedAt,
            int startPage,
            int endPage,
            int pagesVisited,
            int rawUrlsTotal,
            int cleanedUrlsTotal,
            int acceptedUrls,
            int rejectedUrls,
            int errorsCount,
            int filteredAlreadyDiscovered
    ) {
        DiscoveryRunStats stats = new DiscoveryRunStats();
        stats.setQuery(query);
        stats.setStartedAt(startedAt);
        stats.setFinishedAt(LocalDateTime.now());
        stats.setStartPage(startPage);
        stats.setEndPage(endPage);
        stats.setPagesVisited(pagesVisited);
        stats.setRawUrls(rawUrlsTotal);
        stats.setCleanedUrls(cleanedUrlsTotal);
        stats.setAcceptedUrls(acceptedUrls);
        stats.setRejectedUrls(rejectedUrls);
        stats.setErrors(errorsCount);
        stats.setFilteredAlreadyDiscovered(filteredAlreadyDiscovered);

        discoveryRunStatsRepository.save(stats);
    }
}