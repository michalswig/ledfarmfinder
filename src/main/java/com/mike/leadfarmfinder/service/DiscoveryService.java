package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.config.LeadFinderProperties;
import com.mike.leadfarmfinder.dto.FarmClassificationResult;
import com.mike.leadfarmfinder.entity.SerpQueryCursor;
import com.mike.leadfarmfinder.service.discovery.DiscoveredUrlWriter;
import com.mike.leadfarmfinder.service.discovery.DiscoveryDuplicateChecker;
import com.mike.leadfarmfinder.service.discovery.DiscoveryQueryScheduler;
import com.mike.leadfarmfinder.service.discovery.DiscoveryRunStatsWriter;
import com.mike.leadfarmfinder.service.discovery.DiscoverySnippetFetcher;
import com.mike.leadfarmfinder.service.discovery.DiscoveryUrlFilter;
import com.mike.leadfarmfinder.service.discovery.DiscoveryUrlNormalizer;
import com.mike.leadfarmfinder.service.discovery.DiscoveryUrlScorer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryService {

    private final DiscoveryUrlNormalizer urlNormalizer;
    private final DiscoveryUrlFilter discoveryUrlFilter;
    private final DiscoveryUrlScorer urlScorer;
    private final DiscoverySnippetFetcher snippetFetcher;
    private final DiscoveryDuplicateChecker duplicateChecker;
    private final DiscoveredUrlWriter discoveredUrlWriter;
    private final DiscoveryRunStatsWriter discoveryRunStatsWriter;
    private final DiscoveryQueryScheduler queryScheduler;
    private final FarmScraperService farmScraperService;

    private final SerpApiService serpApiService;
    private final OpenAiFarmClassifier farmClassifier;
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

        Optional<DiscoveryQueryScheduler.QueryPick> pickOpt = queryScheduler.pickNextNonExhaustedQuery(queries);
        if (pickOpt.isEmpty()) {
            log.info("DiscoveryService: all queries exhausted (all cursors DONE). Nothing to do.");
            return List.of();
        }

        DiscoveryQueryScheduler.QueryPick pick = pickOpt.get();
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

        if (queryScheduler.isExhausted(cursor)) {
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
                EmptySerpPageOutcome emptySerpPageOutcome = handleEmptySerpPage(
                        rawQuery,
                        currentPage,
                        maxPage,
                        consecutiveEmptySerpPages
                );
                currentPage = emptySerpPageOutcome.currentPage();
                consecutiveEmptySerpPages = emptySerpPageOutcome.consecutiveEmptySerpPages();
                break;
            }

            pagesVisited++;

            List<String> cleaned = cleanSerpUrls(rawUrls);
            cleanedUrlsTotal += cleaned.size();

            log.info("DiscoveryService: urls after domain filter (page={}) = {}", currentPage, cleaned.size());

            NewUrlSelectionOutcome newUrlSelectionOutcome = selectNewUrlsForClassification(cleaned, accepted.size(), limit);
            List<String> newUrlsOnly = newUrlSelectionOutcome.newUrlsOnly();
            normalizedChanged += newUrlSelectionOutcome.normalizedChangedDelta();
            filteredAsAlreadyDiscovered += newUrlSelectionOutcome.filteredAlreadyDiscoveredDelta();
            alreadySeenSkipped += newUrlSelectionOutcome.alreadySeenSkippedDelta();
            rejectedCount += newUrlSelectionOutcome.rejectedDelta();

            openAiCandidates += newUrlsOnly.size();

            log.info(
                    "DiscoveryService: new urls for OpenAI after discovered filter (page={}) = {}",
                    currentPage, newUrlsOnly.size()
            );

            EmptyNewCandidatesOutcome emptyNewCandidatesOutcome = handleEmptyNewCandidates(
                    newUrlsOnly,
                    rawQuery,
                    currentPage,
                    maxPage,
                    consecutiveEmptyNewUrls,
                    consecutiveEmptySerpPages
            );
            currentPage = emptyNewCandidatesOutcome.currentPage();
            consecutiveEmptyNewUrls = emptyNewCandidatesOutcome.consecutiveEmptyNewUrls();
            consecutiveEmptySerpPages = emptyNewCandidatesOutcome.consecutiveEmptySerpPages();

            if (emptyNewCandidatesOutcome.shouldBreak()) {
                break;
            }
            if (emptyNewCandidatesOutcome.shouldContinue()) {
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

                ScoredUrlProcessingOutcome scoredUrlProcessingOutcome = processScoredUrl(scoredUrl, accepted);
                rejectedCount += scoredUrlProcessingOutcome.rejectedDelta();
                errorsCount += scoredUrlProcessingOutcome.errorsDelta();
            }

            currentPage = queryScheduler.advancePageOrExhaust(currentPage, maxPage);
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
        queryScheduler.saveCursorAfterRun(cursor, currentPage);

        List<String> distinctAccepted = accepted.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        discoveryRunStatsWriter.save(
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

        return distinctAccepted;
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
    ) {
    }

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

            int nextPage = queryScheduler.advancePageOrExhaust(currentPage, maxPage);
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
    ) {
    }

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

        int nextPage = queryScheduler.advancePageOrExhaust(currentPage, maxPage);
        return new EmptySerpPageOutcome(nextPage, nextEmptySerpPages);
    }

    private record NewUrlSelectionOutcome(
            List<String> newUrlsOnly,
            int normalizedChangedDelta,
            int filteredAlreadyDiscoveredDelta,
            int alreadySeenSkippedDelta,
            int rejectedDelta
    ) {
    }

    private NewUrlSelectionOutcome selectNewUrlsForClassification(List<String> cleaned, int acceptedSize, int limit) {
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

            if (duplicateChecker.checkAlreadySeen(normalized) != DiscoveryDuplicateChecker.SeenDecision.NOT_SEEN) {
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

        return new NewUrlSelectionOutcome(
                newUrlsOnly,
                normalizedChangedDelta,
                filteredAlreadyDiscoveredDelta,
                alreadySeenSkippedDelta,
                rejectedDelta
        );
    }

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

    private record ScoredUrl(String url, int score) {
    }

    private record ScoredUrlProcessingOutcome(int rejectedDelta, int errorsDelta) {
    }

    private ScoredUrlProcessingOutcome processScoredUrl(ScoredUrl scoredUrl, List<String> accepted) {
        String url = scoredUrl.url();
        try {
            String snippet = snippetFetcher.fetchTextSnippet(url);

            if (snippet == null || snippet.isBlank()) {

                if (shouldTryDirectScrape(url, scoredUrl.score())) {
                    var recoveredLeads = farmScraperService.scrapeFarmLeads(url);

                    if (recoveredLeads != null && !recoveredLeads.isEmpty()) {
                        accepted.add(url);

                        log.info(
                                "DiscoveryService: RECOVERED via direct scrape url={} score={} leadsFound={}",
                                url, scoredUrl.score(), recoveredLeads.size()
                        );

                        return new ScoredUrlProcessingOutcome(0, 0);
                    }

                    log.info(
                            "DiscoveryService: direct scrape found no leads for high-score url={} score={}",
                            url, scoredUrl.score()
                    );
                }

                log.info(
                        "DiscoveryService: SKIP (empty/low-quality snippet - no OpenAI, no DB save) url={} score={}",
                        url, scoredUrl.score()
                );
                return new ScoredUrlProcessingOutcome(1, 0);
            }

            FarmClassificationResult result = farmClassifier.classifyFarm(url, snippet);
            discoveredUrlWriter.save(url, result);
            return handleClassificationResult(scoredUrl, result, accepted);

        } catch (Exception e) {
            log.warn(
                    "DiscoveryService: error for url={} (score={}): {}",
                    url, scoredUrl.score(), e.getMessage()
            );
            return new ScoredUrlProcessingOutcome(0, 1);
        }
    }

    private boolean shouldTryDirectScrape(String url, int score) {
        if (score < 50) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return DIRECT_SCRAPE_HINTS.stream().anyMatch(lower::contains);
    }

    private static final List<String> DIRECT_SCRAPE_HINTS = List.of(
            "spargelhof",
            "obsthof",
            "landhof",
            "bauernhof",
            "gemuesehof",
            "gemüsehof",
            "beerenhof",
            "erdbeerhof",
            "biohof",
            "hofladen",
            "ab-hof",
            "hofverkauf",
            "direktvermarktung"
    );

    private ScoredUrlProcessingOutcome handleClassificationResult(
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
            return new ScoredUrlProcessingOutcome(0, 0);
        }

        log.info(
                "DiscoveryService: REJECTED (NOT A FARM) url={} score={} seasonalJobs={} reason={}",
                url, scoredUrl.score(), result.isSeasonalJobs(), result.reason()
        );
        return new ScoredUrlProcessingOutcome(1, 0);
    }
}