package com.mike.leadfarmfinder.service.discovery;

import com.mike.leadfarmfinder.repository.DiscoveredUrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiscoveryDuplicateChecker {

    private final DiscoveredUrlRepository discoveredUrlRepository;

    public SeenDecision checkAlreadySeen(String normalizedUrl) {
        if (discoveredUrlRepository.existsByUrl(normalizedUrl)) {
            return SeenDecision.SEEN_BY_URL;
        }
        return SeenDecision.NOT_SEEN;
    }

    public enum SeenDecision {
        NOT_SEEN,
        SEEN_BY_URL
    }
}