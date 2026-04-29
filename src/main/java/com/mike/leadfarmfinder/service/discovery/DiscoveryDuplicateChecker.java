package com.mike.leadfarmfinder.service.discovery;

import com.mike.leadfarmfinder.repository.DiscoveredUrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiscoveryDuplicateChecker {

    private final DiscoveredUrlRepository discoveredUrlRepository;

    public SeenDecision checkAlreadySeen(String normalizedUrl, String normalizedDomain) {

        if (discoveredUrlRepository.existsByUrl(normalizedUrl)) {
            return SeenDecision.SEEN_BY_URL;
        }

        if (normalizedDomain != null
                && !normalizedDomain.isBlank()
                && discoveredUrlRepository.existsByDomain(normalizedDomain)) {
            return SeenDecision.SEEN_BY_DOMAIN;
        }

        return SeenDecision.NOT_SEEN;
    }

    public enum SeenDecision {
        NOT_SEEN,
        SEEN_BY_URL,
        SEEN_BY_DOMAIN
    }
}