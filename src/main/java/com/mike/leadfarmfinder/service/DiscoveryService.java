package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.dto.FarmClassificationResult;
import com.mike.leadfarmfinder.entity.DiscoveredUrl;
import com.mike.leadfarmfinder.entity.DiscoveryRunStats;
import com.mike.leadfarmfinder.entity.SerpQueryCursor;
import com.mike.leadfarmfinder.repository.DiscoveredUrlRepository;
import com.mike.leadfarmfinder.repository.DiscoveryRunStatsRepository;
import com.mike.leadfarmfinder.repository.SerpQueryCursorRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryService {

    private final SerpApiService serpApiService;
    private final OpenAiFarmClassifier farmClassifier;
    private final SerpQueryCursorRepository serpQueryCursorRepository;
    private final DiscoveryRunStatsRepository discoveryRunStatsRepository;
    private final DiscoveredUrlRepository discoveredUrlRepository;

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

    // --- Paginacja SERP ---
    private static final int RESULTS_PER_PAGE = 10;
    private static final int MAX_PAGES_PER_RUN = 3;
    private static final int DEFAULT_MAX_SERP_PAGE = 50;

    /**
     * Znajdź kandydackie URLe gospodarstw:
     *  1) SerpAPI -> kilka stron SERP
     *  2) filtr po domenie (BLOCKED_DOMAINS)
     *  3) filtr "już odkryte" (discovered_urls)
     *  4) OpenAI classifier (is_farm && is_seasonal_jobs)
     *  5) zapis statystyk do discovery_run_stats
     *  6) zapis każdego sklasyfikowanego URL do discovered_urls
     */
    public List<String> findCandidateFarmUrls(int limit) {
        String query = "kleine Landwirtschaft Gemüse Obst Deutschland";
        LocalDateTime startedAt = LocalDateTime.now();

        log.info("DiscoveryService: searching farms for query='{}', limit={}", query, limit);

        SerpQueryCursor cursor = loadOrCreateCursor(query);
        int startPage = cursor.getCurrentPage();
        int currentPage = startPage;
        int maxPage = cursor.getMaxPage();

        log.info("DiscoveryService: starting SERP from page={} (maxPage={})", startPage, maxPage);

        List<String> accepted = new ArrayList<>();

        // statystyki
        int pagesVisited = 0;
        int rawUrlsTotal = 0;
        int cleanedUrlsTotal = 0;
        int filteredAsAlreadyDiscovered = 0;
        int acceptedCount = 0;
        int rejectedCount = 0;
        int errorsCount = 0;

        for (int i = 0; i < MAX_PAGES_PER_RUN && accepted.size() < limit; i++) {
            log.info("DiscoveryService: fetching SERP page={} (runPageIndex={})", currentPage, i);

            List<String> rawUrls = serpApiService.searchUrls(query, RESULTS_PER_PAGE, currentPage);
            rawUrlsTotal += rawUrls.size();

            log.info("DiscoveryService: raw urls from SerpAPI (page={}) = {}", currentPage, rawUrls.size());

            if (rawUrls.isEmpty()) {
                log.info("DiscoveryService: no more results from SerpAPI for page={}, stopping", currentPage);
                break;
            }

            pagesVisited++;

            // 1) filtr domen
            List<String> cleaned = rawUrls.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .filter(this::isAllowedDomain)
                    .distinct()
                    .collect(Collectors.toList());

            cleanedUrlsTotal += cleaned.size();

            log.info("DiscoveryService: urls after domain filter (page={}) = {}", currentPage, cleaned.size());

            // 2) filtr "już odkryte" w discovered_urls
            List<String> newUrlsOnly = filterAlreadyDiscovered(cleaned);
            filteredAsAlreadyDiscovered += (cleaned.size() - newUrlsOnly.size());

            log.info("DiscoveryService: urls after discovered filter (page={}) = {}", currentPage, newUrlsOnly.size());

            // 3) OpenAI classifier na nowo odkrytych
            for (String url : newUrlsOnly) {
                if (accepted.size() >= limit) {
                    break;
                }

                try {
                    String snippet = fetchTextSnippet(url);
                    if (snippet.isBlank()) {
                        log.info("DiscoveryService: empty snippet for url={}, skipping", url);
                        rejectedCount++; // traktujemy jako odrzucone
                        continue;
                    }

                    FarmClassificationResult result = farmClassifier.classifyFarm(url, snippet);

                    // zapis do discovered_urls – niezależnie, czy ACCEPT, czy REJECT
                    saveDiscoveredUrl(url, result);

                    if (result.isFarm() && result.isSeasonalJobs()) {
                        String finalUrl = result.mainContactUrl() != null
                                ? result.mainContactUrl()
                                : url;

                        accepted.add(finalUrl);
                        acceptedCount++;
                        log.info("DiscoveryService: ACCEPTED url={} reason={}", finalUrl, result.reason());
                    } else {
                        rejectedCount++;
                        log.info("DiscoveryService: REJECTED url={} reason={}", url, result.reason());
                    }

                } catch (Exception e) {
                    errorsCount++;
                    log.warn("DiscoveryService: error for url={}: {}", url, e.getMessage());
                }
            }

            currentPage++;
            if (currentPage > maxPage) {
                currentPage = 1;
            }
        }

        // update kursora
        cursor.setCurrentPage(currentPage);
        cursor.setLastRunAt(LocalDateTime.now());
        serpQueryCursorRepository.save(cursor);

        // finalne distinct
        List<String> distinctAccepted = accepted.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        // dopasuj acceptedCount do distinct (na wszelki wypadek)
        acceptedCount = distinctAccepted.size();

        // zapis statystyk
        DiscoveryRunStats stats = new DiscoveryRunStats();
        stats.setQuery(query);
        stats.setStartedAt(startedAt);
        stats.setFinishedAt(LocalDateTime.now());
        stats.setStartPage(startPage);
        stats.setEndPage(currentPage);
        stats.setPagesVisited(pagesVisited);
        stats.setRawUrls(rawUrlsTotal);
        stats.setCleanedUrls(cleanedUrlsTotal);
        stats.setAcceptedUrls(acceptedCount);
        stats.setRejectedUrls(rejectedCount);
        stats.setErrors(errorsCount);
        stats.setFilteredAlreadyDiscovered(filteredAsAlreadyDiscovered);

        discoveryRunStatsRepository.save(stats);

        log.info(
                "DiscoveryService: returning {} accepted urls (startPage={}, endPage={}, pagesVisited={}, filteredAlreadyDiscovered={})",
                distinctAccepted.size(), startPage, currentPage, pagesVisited, filteredAsAlreadyDiscovered
        );

        return distinctAccepted;
    }

    /**
     * Ładuje istniejący kursor SERP dla danego query albo tworzy nowy.
     */
    private SerpQueryCursor loadOrCreateCursor(String query) {
        return serpQueryCursorRepository.findByQuery(query)
                .orElseGet(() -> {
                    SerpQueryCursor cursor = new SerpQueryCursor();
                    cursor.setQuery(query);
                    cursor.setCurrentPage(1);
                    cursor.setMaxPage(DEFAULT_MAX_SERP_PAGE);
                    cursor.setLastRunAt(null);
                    SerpQueryCursor saved = serpQueryCursorRepository.save(cursor);
                    log.info("DiscoveryService: created new SERP cursor for query='{}'", query);
                    return saved;
                });
    }

    /**
     * Filtruje URLe, które już są w discovered_urls.
     */
    private List<String> filterAlreadyDiscovered(List<String> urls) {
        List<String> result = urls.stream()
                .filter(url -> {
                    boolean exists = discoveredUrlRepository.existsByUrl(url);
                    if (exists) {
                        log.debug("DiscoveryService: skipping already discovered url={}", url);
                    }
                    return !exists;
                })
                .toList();

        log.info("DiscoveryService: {} urls left after already-discovered filter (from {})",
                result.size(), urls.size());

        return result;
    }

    /**
     * Upsert do discovered_urls po każdym wyniku klasyfikacji.
     */
    private void saveDiscoveredUrl(String url, FarmClassificationResult result) {
        try {
            DiscoveredUrl entity = discoveredUrlRepository.findByUrl(url)
                    .orElseGet(DiscoveredUrl::new);

            boolean isNew = (entity.getId() == null);

            entity.setUrl(url);
            entity.setFarm(result.isFarm());
            entity.setSeasonalJobs(result.isSeasonalJobs());
            entity.setLastSeenAt(LocalDateTime.now());

            if (isNew) {
                entity.setFirstSeenAt(LocalDateTime.now());
            }

            discoveredUrlRepository.save(entity);

            if (isNew) {
                log.info("DiscoveryService: saved NEW discovered url={} (farm={}, seasonalJobs={})",
                        url, result.isFarm(), result.isSeasonalJobs());
            } else {
                log.debug("DiscoveryService: updated discovered url={} (farm={}, seasonalJobs={})",
                        url, result.isFarm(), result.isSeasonalJobs());
            }
        } catch (Exception e) {
            log.warn("DiscoveryService: failed to save discovered url={} due to {}", url, e.getMessage());
        }
    }

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

        if (domain.contains("zeitung") || domain.contains("news")) {
            log.info("DiscoveryService: dropping url={} (looks like news/media domain={})", url, domain);
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
            String host = uri.getHost();
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
