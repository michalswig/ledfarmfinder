package com.mike.leadfarmfinder.service.discovery;

import com.mike.leadfarmfinder.dto.FarmClassificationResult;
import com.mike.leadfarmfinder.entity.DiscoveredUrl;
import com.mike.leadfarmfinder.repository.DiscoveredUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DiscoveredUrlWriter {

    private final DiscoveredUrlRepository discoveredUrlRepository;
    private final DiscoveryUrlNormalizer urlNormalizer;

    public void save(String url, FarmClassificationResult result) {
        try {
            DiscoveredUrl entity = discoveredUrlRepository.findByUrl(url)
                    .orElseGet(DiscoveredUrl::new);

            boolean isNew = entity.getId() == null;
            LocalDateTime now = LocalDateTime.now();

            entity.setUrl(url);
            entity.setDomain(urlNormalizer.extractNormalizedDomain(url));
            entity.setFarm(result.isFarm());
            entity.setSeasonalJobs(result.isSeasonalJobs());
            entity.setLastSeenAt(now);

            if (isNew) {
                entity.setFirstSeenAt(now);
            }

            discoveredUrlRepository.save(entity);

            if (isNew) {
                log.info(
                        "DiscoveredUrlWriter: saved NEW discovered url={} (farm={}, seasonalJobs={})",
                        url, result.isFarm(), result.isSeasonalJobs()
                );
            } else {
                log.debug(
                        "DiscoveredUrlWriter: updated discovered url={} (farm={}, seasonalJobs={})",
                        url, result.isFarm(), result.isSeasonalJobs()
                );
            }
        } catch (Exception e) {
            log.warn("DiscoveredUrlWriter: failed to save discovered url={} due to {}", url, e.getMessage());
        }
    }
}