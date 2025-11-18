package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.dto.FarmClassificationResult;
import com.mike.leadfarmfinder.service.openai.OpenAiFarmClassifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryService {

    private final SerpApiService serpApiService;
    private final OpenAiFarmClassifier farmClassifier;

    // duże portale, social media, job-boardy – od razu odrzucamy
    private static final Set<String> BLOCKED_DOMAINS = Set.of(
            "indeed.com",
            "de.indeed.com",
            "instagram.com",
            "facebook.com",
            "m.facebook.com",
            "youtube.com",
            "linkedin.com",
            "www.linkedin.com",
            "tiktok.com",
            "xing.com",
            "stepstone.de",
            "meinestadt.de"
    );

    /**
     * Znajdź kandydackie URLe gospodarstw:
     *  1) SerpAPI -> lista URLi
     *  2) filtr po domenie (BLOCKED_DOMAINS)
     *  3) OpenAI classifier (is_farm && is_seasonal_jobs)
     */
    public List<String> findCandidateFarmUrls(int limit) {
        String query = "Saisonarbeit Erdbeeren Hof Niedersachsen";

        log.info("DiscoveryService: searching farms for query='{}', limit={}", query, limit);

        List<String> rawUrls = serpApiService.searchUrls(query, limit * 5);
        log.info("DiscoveryService: raw urls from SerpAPI = {}", rawUrls.size());

        // 1) wstępne czyszczenie + filtr domen
        List<String> cleaned = rawUrls.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .filter(this::isAllowedDomain)
                .distinct()
                .collect(Collectors.toList());

        log.info("DiscoveryService: urls after domain filter = {}", cleaned.size());

        // 2) OpenAI classifier
        List<String> accepted = new ArrayList<>();

        for (String url : cleaned) {
            try {
                String snippet = fetchTextSnippet(url);
                if (snippet.isBlank()) {
                    log.info("DiscoveryService: empty snippet for url={}, skipping", url);
                    continue;
                }

                FarmClassificationResult result = farmClassifier.classifyFarm(url, snippet);

                if (result.isFarm() && result.isSeasonalJobs()) {
                    String finalUrl = result.mainContactUrl() != null
                            ? result.mainContactUrl()
                            : url;

                    accepted.add(finalUrl);
                    log.info("DiscoveryService: ACCEPTED url={} reason={}", finalUrl, result.reason());
                } else {
                    log.info("DiscoveryService: REJECTED url={} reason={}", url, result.reason());
                }

                if (accepted.size() >= limit) {
                    break;
                }

            } catch (Exception e) {
                log.warn("DiscoveryService: error for url={}: {}", url, e.getMessage());
            }
        }

        // 3) finalne odszumienie
        List<String> distinctAccepted = accepted.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        log.info("DiscoveryService: returning {} accepted urls", distinctAccepted.size());
        return distinctAccepted;
    }

    /**
     * Zwraca true tylko jeśli domena NIE jest na blackliście.
     */
    private boolean isAllowedDomain(String url) {
        String domain = extractDomain(url);
        if (domain == null) {
            log.info("DiscoveryService: dropping url={} (no domain)", url);
            return false;
        }

        if (BLOCKED_DOMAINS.contains(domain)) {
            log.info("DiscoveryService: dropping url={} (blocked domain={})", url, domain);
            return false;
        }

        return true;
    }

    /**
     * Wyciąga domenę z URL-a:
     *  https://www.instagram.com/p/... -> instagram.com
     *  https://erdbeeren-hannover.de/jobs/ -> erdbeeren-hannover.de
     */
    private String extractDomain(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost(); // np. "www.instagram.com"
            if (host == null) return null;

            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            return host;
        } catch (Exception e) {
            log.warn("DiscoveryService: failed to extract domain from {}: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * Pobiera HTML strony i wyciąga max 2000 znaków widocznego tekstu.
     */
    private String fetchTextSnippet(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; LeadFarmFinderBot/1.0)")
                    .timeout(10_000)
                    .get();

            String text = doc.text();
            if (text == null) {
                return "";
            }

            int maxLen = 2000;
            return text.length() > maxLen ? text.substring(0, maxLen) : text;
        } catch (Exception e) {
            log.warn("DiscoveryService: failed to fetch text from {}: {}", url, e.getMessage());
            return "";
        }
    }
}
