package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.config.LeadFinderProperties;
import com.mike.leadfarmfinder.dto.FarmClassificationResult;
import com.mike.leadfarmfinder.entity.DiscoveryRunStats;
import com.mike.leadfarmfinder.entity.SerpQueryCursor;
import com.mike.leadfarmfinder.repository.DiscoveredUrlRepository;
import com.mike.leadfarmfinder.repository.DiscoveryRunStatsRepository;
import com.mike.leadfarmfinder.repository.SerpQueryCursorRepository;
import com.mike.leadfarmfinder.service.discovery.DiscoveryUrlFilter;
import com.mike.leadfarmfinder.service.discovery.DiscoveryUrlNormalizer;
import com.mike.leadfarmfinder.service.discovery.DiscoveryUrlScorer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryService {

    private final DiscoveryUrlNormalizer urlNormalizer;
    private final DiscoveryUrlFilter discoveryUrlFilter;
    private final DiscoveryUrlScorer urlScorer;

    private final SerpApiService serpApiService;
    private final OpenAiFarmClassifier farmClassifier;
    private final SerpQueryCursorRepository serpQueryCursorRepository;
    private final DiscoveryRunStatsRepository discoveryRunStatsRepository;
    private final DiscoveredUrlRepository discoveredUrlRepository;
    private final LeadFinderProperties leadFinderProperties;

    private static final List<String> QUERY_NEGATIVE_TOKENS = List.of(
            "-branchenbuch",
            "-gelbeseiten",
            "-11880",
            "-cylex",
            "-golocal",
            "-meinestadt",
            "-facebook",
            "-instagram",
            "-linkedin",
            "-xing",
            "-tiktok",
            "-youtube",
            "-stepstone",
            "-indeed",
            "-job",
            "-jobs",
            "-karriere",
            "-stellenangebote",
            "-zeitung",
            "-news",
            "-tourismus",
            "-urlaub",
            "-booking",
            "-airbnb"
    );

    private static final int EXHAUST_AFTER_EMPTY_NEW_URL_PAGES = 2;
    private static final int EXHAUST_AFTER_EMPTY_SERP_PAGES = 1;

    private int queryIndex = 0;

    public List<String> findCandidateFarmUrls(int limit) {

        int alreadySeenSkipped = 0;
        int normalizedChanged = 0;
        int openAiCandidates = 0;

        int resultsPerPage = leadFinderProperties.getDiscovery().getResultsPerPage();
        int maxPagesPerRun = leadFinderProperties.getDiscovery().getMaxPagesPerRun();

        List<String> queries = leadFinderProperties.getDiscovery().getQueries();
        if (queries == null || queries.isEmpty()) {
            throw new IllegalStateException("Discovery queries are not configured! Add leadfinder.discovery.queries[]");
        }

        Optional<QueryPick> pickOpt = pickNextNonExhaustedQuery(queries);
        if (pickOpt.isEmpty()) {
            log.info("DiscoveryService: all queries exhausted (all cursors DONE). Nothing to do.");
            return List.of();
        }

        QueryPick pick = pickOpt.get();
        int currentQueryIndex = pick.index();
        String rawQuery = pick.query();
        SerpQueryCursor cursor = pick.cursor();

        String query = withQueryNegatives(rawQuery);
        LocalDateTime startedAt = LocalDateTime.now();

        log.info(
                "DiscoveryService: searching farms for query='{}' (queryIndex={}), limit={}, resultsPerPage={}, maxPagesPerRun={}",
                rawQuery, currentQueryIndex, limit, resultsPerPage, maxPagesPerRun
        );

        if (!query.equals(rawQuery)) {
            log.info("DiscoveryService: SERP query after negatives='{}'", query);
        }

        int startPage = cursor.getCurrentPage();
        int currentPage = startPage;
        int maxPage = cursor.getMaxPage();

        if (isExhausted(cursor)) {
            log.info(
                    "DiscoveryService: picked query is already exhausted (DONE). query='{}', currentPage={}, maxPage={}",
                    rawQuery, startPage, maxPage
            );
            return List.of();
        }

        log.info(
                "DiscoveryService: starting SERP from page={} (maxPage={}) for query='{}'",
                startPage, maxPage, rawQuery
        );

        List<String> accepted = new ArrayList<>();

        int pagesVisited = 0;
        int rawUrlsTotal = 0;
        int cleanedUrlsTotal = 0;
        int filteredAsAlreadyDiscovered = 0;
        int rejectedCount = 0;
        int errorsCount = 0;

        int consecutiveEmptyNewUrls = 0;
        int consecutiveEmptySerpPages = 0;

        for (int i = 0; i < maxPagesPerRun && accepted.size() < limit; i++) {

            log.info(
                    "DiscoveryService: fetching SERP page={} (runPageIndex={}) for query='{}'",
                    currentPage, i, rawQuery
            );

            List<String> rawUrls = serpApiService.searchUrls(query, resultsPerPage, currentPage);
            rawUrlsTotal += rawUrls.size();

            log.info("DiscoveryService: raw urls from SerpAPI (page={}) = {}", currentPage, rawUrls.size());

            if (rawUrls.isEmpty()) {
                consecutiveEmptySerpPages++;

                log.info(
                        "DiscoveryService: empty SERP page streak = {} (page={})",
                        consecutiveEmptySerpPages, currentPage
                );

                if (consecutiveEmptySerpPages >= EXHAUST_AFTER_EMPTY_SERP_PAGES) {
                    currentPage = maxPage + 1;
                    log.info(
                            "DiscoveryService: marking query as DONE due to empty SERP response. query='{}', pageNow={}",
                            rawQuery, currentPage
                    );
                    break;
                }

                currentPage = advancePageOrExhaust(currentPage, maxPage);
                break;
            }

            pagesVisited++;

            List<String> cleaned = rawUrls.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .filter(urlNormalizer::isNotFileUrl)
                    .filter(discoveryUrlFilter::isAllowedDomain)
                    .distinct()
                    .toList();

            cleanedUrlsTotal += cleaned.size();

            log.info("DiscoveryService: urls after domain filter (page={}) = {}", currentPage, cleaned.size());

            List<String> newUrlsOnly = new ArrayList<>();
            Set<String> normalizedSeenThisPage = new HashSet<>();

            for (String url : cleaned) {
                if (accepted.size() >= limit) {
                    break;
                }

                String normalized = urlNormalizer.normalizeUrl(url);

                if (!normalized.equals(url)) {
                    normalizedChanged++;
                }

                if (!normalizedSeenThisPage.add(normalized)) {
                    continue;
                }

                if (checkAlreadySeen(normalized) != SeenDecision.NOT_SEEN) {
                    filteredAsAlreadyDiscovered++;
                    alreadySeenSkipped++;
                    continue;
                }

                if (discoveryUrlFilter.isHardNegativePath(normalized)) {
                    rejectedCount++;
                    log.info("DiscoveryService: SKIP (hard-negative-path - no DB save) url={}", normalized);
                    continue;
                }

                newUrlsOnly.add(normalized);
            }

            openAiCandidates += newUrlsOnly.size();

            log.info(
                    "DiscoveryService: new urls for OpenAI after discovered filter (page={}) = {}",
                    currentPage, newUrlsOnly.size()
            );

            if (newUrlsOnly.isEmpty()) {
                consecutiveEmptyNewUrls++;

                log.info(
                        "DiscoveryService: empty NEW candidates streak = {} (page={})",
                        consecutiveEmptyNewUrls, currentPage
                );

                if (consecutiveEmptyNewUrls >= EXHAUST_AFTER_EMPTY_NEW_URL_PAGES) {
                    currentPage = maxPage + 1;
                    log.info(
                            "DiscoveryService: marking query as DONE after {} empty NEW pages. query='{}', pageNow={}",
                            consecutiveEmptyNewUrls, rawQuery, currentPage
                    );
                    break;
                }

                currentPage = advancePageOrExhaust(currentPage, maxPage);
                if (currentPage > maxPage) {
                    break;
                }
                continue;
            } else {
                consecutiveEmptyNewUrls = 0;
                consecutiveEmptySerpPages = 0;
            }

            List<ScoredUrl> scored = newUrlsOnly.stream()
                    .map(u -> new ScoredUrl(u, urlScorer.computeDomainPriorityScore(u)))
                    .sorted(Comparator.comparingInt(ScoredUrl::score).reversed())
                    .toList();

            log.info(
                    "DiscoveryService: scored {} NEW urls (top example: {})",
                    scored.size(),
                    scored.isEmpty() ? "none" : (scored.get(0).url() + " score=" + scored.get(0).score())
            );

            for (ScoredUrl scoredUrl : scored) {

                if (accepted.size() >= limit) {
                    break;
                }

                String url = scoredUrl.url();

                try {
                    String snippet = fetchTextSnippet(url);

                    if (snippet == null || snippet.isBlank()) {
                        rejectedCount++;
                        log.info(
                                "DiscoveryService: SKIP (empty/low-quality snippet - no OpenAI, no DB save) url={} score={}",
                                url, scoredUrl.score()
                        );
                        continue;
                    }

                    FarmClassificationResult result = farmClassifier.classifyFarm(url, snippet);
                    saveDiscoveredUrl(url, result);

                    if (result.isFarm()) {
                        accepted.add(url);

                        String contactUrl = result.mainContactUrl();
                        if (contactUrl != null && !contactUrl.isBlank()) {
                            accepted.add(contactUrl);
                        }

                        log.info(
                                "DiscoveryService: ACCEPTED (FARM) sourceUrl={} contactUrl={} score={} seasonalJobs={} reason={}",
                                url, contactUrl, scoredUrl.score(), result.isSeasonalJobs(), result.reason()
                        );

                    } else {
                        rejectedCount++;
                        log.info(
                                "DiscoveryService: REJECTED (NOT A FARM) url={} score={} seasonalJobs={} reason={}",
                                url, scoredUrl.score(), result.isSeasonalJobs(), result.reason()
                        );
                    }

                } catch (Exception e) {
                    errorsCount++;
                    log.warn(
                            "DiscoveryService: error for url={} (score={}): {}",
                            url, scoredUrl.score(), e.getMessage()
                    );
                }
            }

            currentPage = advancePageOrExhaust(currentPage, maxPage);
            if (currentPage > maxPage) {
                break;
            }
        }

        cursor.setCurrentPage(currentPage);
        cursor.setLastRunAt(LocalDateTime.now());
        serpQueryCursorRepository.save(cursor);

        List<String> distinctAccepted = accepted.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        DiscoveryRunStats stats = new DiscoveryRunStats();
        stats.setQuery(rawQuery);
        stats.setStartedAt(startedAt);
        stats.setFinishedAt(LocalDateTime.now());
        stats.setStartPage(startPage);
        stats.setEndPage(currentPage);
        stats.setPagesVisited(pagesVisited);
        stats.setRawUrls(rawUrlsTotal);
        stats.setCleanedUrls(cleanedUrlsTotal);
        stats.setAcceptedUrls(distinctAccepted.size());
        stats.setRejectedUrls(rejectedCount);
        stats.setErrors(errorsCount);
        stats.setFilteredAlreadyDiscovered(filteredAsAlreadyDiscovered);

        discoveryRunStatsRepository.save(stats);

        log.info(
                "DiscoveryService: returning {} accepted urls (query='{}', startPage={}, endPage={}, done={}, pagesVisited={}, alreadySeenSkippedUrl={}, openAiCandidates={}, normalizedChanged={})",
                distinctAccepted.size(), rawQuery, startPage, currentPage, (currentPage > maxPage), pagesVisited,
                alreadySeenSkipped, openAiCandidates, normalizedChanged
        );

        return distinctAccepted;
    }

    private boolean isExhausted(SerpQueryCursor c) {
        return c.getCurrentPage() > c.getMaxPage();
    }

    private int advancePageOrExhaust(int currentPage, int maxPage) {
        int next = currentPage + 1;
        if (next > maxPage) {
            return maxPage + 1;
        }
        return next;
    }

    private Optional<QueryPick> pickNextNonExhaustedQuery(List<String> queries) {
        for (int attempts = 0; attempts < queries.size(); attempts++) {
            int idx = queryIndex;
            String q = queries.get(idx);
            queryIndex = (queryIndex + 1) % queries.size();

            SerpQueryCursor c = loadOrCreateCursor(q);
            if (!isExhausted(c)) {
                return Optional.of(new QueryPick(idx, q, c));
            }
        }
        return Optional.empty();
    }

    private record QueryPick(int index, String query, SerpQueryCursor cursor) {}

    private String withQueryNegatives(String rawQuery) {
        String q = rawQuery == null ? "" : rawQuery.trim();
        if (q.isBlank()) {
            return q;
        }

        String lower = q.toLowerCase(Locale.ROOT);
        boolean hasAnyMinus = lower.contains(" -branchenbuch")
                || lower.contains(" -gelbeseiten")
                || lower.contains(" -11880")
                || lower.contains(" -cylex");

        if (hasAnyMinus) {
            return q;
        }

        StringBuilder sb = new StringBuilder(q);
        for (String neg : QUERY_NEGATIVE_TOKENS) {
            sb.append(' ').append(neg);
        }
        return sb.toString();
    }

    private SeenDecision checkAlreadySeen(String normalizedUrl) {
        if (discoveredUrlRepository.existsByUrl(normalizedUrl)) {
            return SeenDecision.SEEN_BY_URL;
        }
        return SeenDecision.NOT_SEEN;
    }

    private enum SeenDecision {
        NOT_SEEN,
        SEEN_BY_URL
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
                    log.info("DiscoveryService: created new SERP cursor for query='{}'", query);
                    return saved;
                });
    }

    private void saveDiscoveredUrl(String url, FarmClassificationResult result) {
        try {
            var entity = discoveredUrlRepository.findByUrl(url)
                    .orElseGet(com.mike.leadfarmfinder.entity.DiscoveredUrl::new);

            boolean isNew = entity.getId() == null;

            entity.setUrl(url);
            entity.setDomain(urlNormalizer.extractNormalizedDomain(url));
            entity.setFarm(result.isFarm());
            entity.setSeasonalJobs(result.isSeasonalJobs());
            entity.setLastSeenAt(LocalDateTime.now());

            if (isNew) {
                entity.setFirstSeenAt(LocalDateTime.now());
            }

            discoveredUrlRepository.save(entity);

            if (isNew) {
                log.info(
                        "DiscoveryService: saved NEW discovered url={} (farm={}, seasonalJobs={})",
                        url, result.isFarm(), result.isSeasonalJobs()
                );
            } else {
                log.debug(
                        "DiscoveryService: updated discovered url={} (farm={}, seasonalJobs={})",
                        url, result.isFarm(), result.isSeasonalJobs()
                );
            }
        } catch (Exception e) {
            log.warn("DiscoveryService: failed to save discovered url={} due to {}", url, e.getMessage());
        }
    }

    private record ScoredUrl(String url, int score) {}

    private String fetchTextSnippet(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; LeadFarmFinderBot/1.0)")
                    .timeout(10_000)
                    .followRedirects(true)
                    .get();

            doc.select("script,style,noscript").remove();

            String text = doc.text();
            if (text == null) {
                return "";
            }

            text = text.trim();

            if (text.length() < 120) {
                return "";
            }

            int maxLen = 2000;
            return text.length() > maxLen ? text.substring(0, maxLen) : text;

        } catch (UnsupportedMimeTypeException e) {
            log.warn("DiscoveryService: failed to fetch text from {}: unsupported mime {}", url, e.getMimeType());
            return "";
        } catch (Exception e) {
            log.warn("DiscoveryService: failed to fetch text from {}: {}", url, e.getMessage());
            return "";
        }
    }
}