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
                EmptySerpPageOutcome serpOutcome = handleEmptySerpPage(
                        rawQuery,
                        currentPage,
                        maxPage,
                        consecutiveEmptySerpPages
                );
                currentPage = serpOutcome.currentPage();
                consecutiveEmptySerpPages = serpOutcome.consecutiveEmptySerpPages();
                break;
            }

            pagesVisited++;

            List<String> cleaned = cleanSerpUrls(rawUrls);

            cleanedUrlsTotal += cleaned.size();

            log.info("DiscoveryService: urls after domain filter (page={}) = {}", currentPage, cleaned.size());

            NewUrlSelectionResult selection = selectNewUrlsForClassification(cleaned, accepted.size(), limit);
            List<String> newUrlsOnly = selection.newUrlsOnly();
            normalizedChanged += selection.normalizedChangedDelta();
            filteredAsAlreadyDiscovered += selection.filteredAlreadyDiscoveredDelta();
            alreadySeenSkipped += selection.alreadySeenSkippedDelta();
            rejectedCount += selection.rejectedDelta();

            openAiCandidates += newUrlsOnly.size();

            log.info(
                    "DiscoveryService: new urls for OpenAI after discovered filter (page={}) = {}",
                    currentPage, newUrlsOnly.size()
            );

            EmptyNewCandidatesOutcome emptyOutcome = handleEmptyNewCandidates(
                    newUrlsOnly,
                    rawQuery,
                    currentPage,
                    maxPage,
                    consecutiveEmptyNewUrls,
                    consecutiveEmptySerpPages
            );
            currentPage = emptyOutcome.currentPage();
            consecutiveEmptyNewUrls = emptyOutcome.consecutiveEmptyNewUrls();
            consecutiveEmptySerpPages = emptyOutcome.consecutiveEmptySerpPages();
            if (emptyOutcome.shouldBreak()) {
                break;
            }
            if (emptyOutcome.shouldContinue()) {
                continue;
            }

            List<ScoredUrl> scored = scoreNewUrls(newUrlsOnly);

            log.info(
                    "DiscoveryService: scored {} NEW urls (top example: {})",
                    scored.size(),
                    scored.isEmpty() ? "none" : (scored.get(0).url() + " score=" + scored.get(0).score())
            );

            for (ScoredUrl scoredUrl : scored) {

                if (accepted.size() >= limit) {
                    break;
                }

                ScoredUrlProcessingDelta delta = processScoredUrl(scoredUrl, accepted);
                rejectedCount += delta.rejectedDelta();
                errorsCount += delta.errorsDelta();
            }

            currentPage = advancePageOrExhaust(currentPage, maxPage);
            if (currentPage > maxPage) {
                break;
            }
        }

        List<String> distinctAccepted = finalizeRun(
                cursor,
                accepted,
                rawQuery,
                startedAt,
                startPage,
                currentPage,
                pagesVisited,
                rawUrlsTotal,
                cleanedUrlsTotal,
                rejectedCount,
                errorsCount,
                filteredAsAlreadyDiscovered
        );

        log.info(
                "DiscoveryService: returning {} accepted urls (query='{}', startPage={}, endPage={}, done={}, pagesVisited={}, alreadySeenSkippedUrl={}, openAiCandidates={}, normalizedChanged={})",
                distinctAccepted.size(), rawQuery, startPage, currentPage, (currentPage > maxPage), pagesVisited,
                alreadySeenSkipped, openAiCandidates, normalizedChanged
        );

        return distinctAccepted;
    }

    private List<String> finalizeRun(
            SerpQueryCursor cursor,
            List<String> accepted,
            String rawQuery,
            LocalDateTime startedAt,
            int startPage,
            int currentPage,
            int pagesVisited,
            int rawUrlsTotal,
            int cleanedUrlsTotal,
            int rejectedCount,
            int errorsCount,
            int filteredAsAlreadyDiscovered
    ) {
        cursor.setCurrentPage(currentPage);
        cursor.setLastRunAt(LocalDateTime.now());
        serpQueryCursorRepository.save(cursor);

        List<String> distinctAccepted = accepted.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        DiscoveryRunStats stats = buildDiscoveryRunStats(
                rawQuery,
                startedAt,
                startPage,
                currentPage,
                pagesVisited,
                rawUrlsTotal,
                cleanedUrlsTotal,
                distinctAccepted.size(),
                rejectedCount,
                errorsCount,
                filteredAsAlreadyDiscovered
        );

        discoveryRunStatsRepository.save(stats);
        return distinctAccepted;
    }

    private DiscoveryRunStats buildDiscoveryRunStats(
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
        return stats;
    }

    private List<String> cleanSerpUrls(List<String> rawUrls) {
        return rawUrls.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .filter(urlNormalizer::isNotFileUrl)
                .filter(discoveryUrlFilter::isAllowedDomain)
                .distinct()
                .toList();
    }

    private List<ScoredUrl> scoreNewUrls(List<String> newUrlsOnly) {
        return newUrlsOnly.stream()
                .map(u -> new ScoredUrl(u, urlScorer.computeDomainPriorityScore(u)))
                .sorted(Comparator.comparingInt(ScoredUrl::score).reversed())
                .toList();
    }

    private record EmptyNewCandidatesOutcome(
            int currentPage,
            int consecutiveEmptyNewUrls,
            int consecutiveEmptySerpPages,
            boolean shouldBreak,
            boolean shouldContinue
    ) {}

    private EmptyNewCandidatesOutcome handleEmptyNewCandidates(
            List<String> newUrlsOnly,
            String rawQuery,
            int currentPage,
            int maxPage,
            int consecutiveEmptyNewUrls,
            int consecutiveEmptySerpPages
    ) {
        if (newUrlsOnly.isEmpty()) {
            int nextEmptyNewUrls = consecutiveEmptyNewUrls + 1;

            log.info(
                    "DiscoveryService: empty NEW candidates streak = {} (page={})",
                    nextEmptyNewUrls, currentPage
            );

            if (nextEmptyNewUrls >= EXHAUST_AFTER_EMPTY_NEW_URL_PAGES) {
                int donePage = maxPage + 1;
                log.info(
                        "DiscoveryService: marking query as DONE after {} empty NEW pages. query='{}', pageNow={}",
                        nextEmptyNewUrls, rawQuery, donePage
                );
                return new EmptyNewCandidatesOutcome(
                        donePage,
                        nextEmptyNewUrls,
                        consecutiveEmptySerpPages,
                        true,
                        false
                );
            }

            int nextPage = advancePageOrExhaust(currentPage, maxPage);
            boolean shouldBreak = nextPage > maxPage;
            return new EmptyNewCandidatesOutcome(
                    nextPage,
                    nextEmptyNewUrls,
                    consecutiveEmptySerpPages,
                    shouldBreak,
                    !shouldBreak
            );
        }

        return new EmptyNewCandidatesOutcome(
                currentPage,
                0,
                0,
                false,
                false
        );
    }

    private record EmptySerpPageOutcome(
            int currentPage,
            int consecutiveEmptySerpPages
    ) {}

    private EmptySerpPageOutcome handleEmptySerpPage(
            String rawQuery,
            int currentPage,
            int maxPage,
            int consecutiveEmptySerpPages
    ) {
        int nextEmptySerpPages = consecutiveEmptySerpPages + 1;

        log.info(
                "DiscoveryService: empty SERP page streak = {} (page={})",
                nextEmptySerpPages, currentPage
        );

        if (nextEmptySerpPages >= EXHAUST_AFTER_EMPTY_SERP_PAGES) {
            int donePage = maxPage + 1;
            log.info(
                    "DiscoveryService: marking query as DONE due to empty SERP response. query='{}', pageNow={}",
                    rawQuery, donePage
            );
            return new EmptySerpPageOutcome(donePage, nextEmptySerpPages);
        }

        int nextPage = advancePageOrExhaust(currentPage, maxPage);
        return new EmptySerpPageOutcome(nextPage, nextEmptySerpPages);
    }

    private record NewUrlSelectionResult(
            List<String> newUrlsOnly,
            int normalizedChangedDelta,
            int filteredAlreadyDiscoveredDelta,
            int alreadySeenSkippedDelta,
            int rejectedDelta
    ) {}

    private NewUrlSelectionResult selectNewUrlsForClassification(List<String> cleaned, int acceptedSize, int limit) {
        List<String> newUrlsOnly = new ArrayList<>();
        Set<String> normalizedSeenThisPage = new HashSet<>();
        int normalizedChangedDelta = 0;
        int filteredAlreadyDiscoveredDelta = 0;
        int alreadySeenSkippedDelta = 0;
        int rejectedDelta = 0;

        for (String url : cleaned) {
            if (acceptedSize >= limit) {
                break;
            }

            String normalized = urlNormalizer.normalizeUrl(url);

            if (!normalized.equals(url)) {
                normalizedChangedDelta++;
            }

            if (!normalizedSeenThisPage.add(normalized)) {
                continue;
            }

            if (checkAlreadySeen(normalized) != SeenDecision.NOT_SEEN) {
                filteredAlreadyDiscoveredDelta++;
                alreadySeenSkippedDelta++;
                continue;
            }

            if (discoveryUrlFilter.isHardNegativePath(normalized)) {
                rejectedDelta++;
                log.info("DiscoveryService: SKIP (hard-negative-path - no DB save) url={}", normalized);
                continue;
            }

            newUrlsOnly.add(normalized);
        }

        return new NewUrlSelectionResult(
                newUrlsOnly,
                normalizedChangedDelta,
                filteredAlreadyDiscoveredDelta,
                alreadySeenSkippedDelta,
                rejectedDelta
        );
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

    private record ScoredUrlProcessingDelta(int rejectedDelta, int errorsDelta) {}

    private ScoredUrlProcessingDelta processScoredUrl(ScoredUrl scoredUrl, List<String> accepted) {
        String url = scoredUrl.url();
        try {
            String snippet = fetchTextSnippet(url);

            if (snippet == null || snippet.isBlank()) {
                log.info(
                        "DiscoveryService: SKIP (empty/low-quality snippet - no OpenAI, no DB save) url={} score={}",
                        url, scoredUrl.score()
                );
                return new ScoredUrlProcessingDelta(1, 0);
            }

            FarmClassificationResult result = farmClassifier.classifyFarm(url, snippet);
            saveDiscoveredUrl(url, result);
            return handleClassificationResult(scoredUrl, result, accepted);

        } catch (Exception e) {
            log.warn(
                    "DiscoveryService: error for url={} (score={}): {}",
                    url, scoredUrl.score(), e.getMessage()
            );
            return new ScoredUrlProcessingDelta(0, 1);
        }
    }

    private ScoredUrlProcessingDelta handleClassificationResult(
            ScoredUrl scoredUrl,
            FarmClassificationResult result,
            List<String> accepted
    ) {
        String url = scoredUrl.url();
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
            return new ScoredUrlProcessingDelta(0, 0);
        }

        log.info(
                "DiscoveryService: REJECTED (NOT A FARM) url={} score={} seasonalJobs={} reason={}",
                url, scoredUrl.score(), result.isSeasonalJobs(), result.reason()
        );
        return new ScoredUrlProcessingDelta(1, 0);
    }

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