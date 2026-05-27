package com.mike.leadfarmfinder.service.directory;

import com.mike.leadfarmfinder.dto.FarmClassificationResult;
import com.mike.leadfarmfinder.service.FarmScraperService;
import com.mike.leadfarmfinder.service.discovery.DiscoveredUrlWriter;
import com.mike.leadfarmfinder.service.discovery.DiscoveryDuplicateChecker;
import com.mike.leadfarmfinder.service.discovery.DiscoveryUrlNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DirectoryCrawlerService {

    private final List<DirectorySource> sources;
    private final DiscoveryDuplicateChecker duplicateChecker;
    private final DiscoveryUrlNormalizer urlNormalizer;
    private final DiscoveredUrlWriter discoveredUrlWriter;
    private final FarmScraperService farmScraperService;

    public List<DirectoryCrawlResult> crawlAll(int maxUrlsPerRun) {
        log.info("DirectoryCrawlerService: starting, sources={}, maxUrlsPerRun={}",
                sources.size(), maxUrlsPerRun);

        List<DirectoryCrawlResult> results = new ArrayList<>();
        int remainingBudget = maxUrlsPerRun;

        for (DirectorySource source : sources) {
            if (remainingBudget <= 0) {
                log.info("DirectoryCrawlerService: budget exhausted, skipping source={}", source.sourceName());
                break;
            }
            DirectoryCrawlResult result = crawlSource(source, remainingBudget);
            results.add(result);
            remainingBudget -= result.urlsProcessed();
        }

        log.info("DirectoryCrawlerService: finished, processed={}, ok={}, errors={}, skippedDuplicate={}",
                results.stream().mapToInt(DirectoryCrawlResult::urlsProcessed).sum(),
                results.stream().mapToInt(DirectoryCrawlResult::urlsScrapedOk).sum(),
                results.stream().mapToInt(DirectoryCrawlResult::urlsScrapedError).sum(),
                results.stream().mapToInt(DirectoryCrawlResult::urlsSkippedDuplicate).sum());

        return results;
    }

    private DirectoryCrawlResult crawlSource(DirectorySource source, int budget) {
        Instant start = Instant.now();
        String name = source.sourceName();

        log.info("DirectoryCrawlerService: fetching urls from source={}", name);

        List<String> rawUrls;
        try {
            rawUrls = source.fetchFarmUrls();
        } catch (Exception e) {
            log.error("DirectoryCrawlerService: source={} failed: {}", name, e.getMessage(), e);
            return new DirectoryCrawlResult(name, 0, 0, 0, 0, 0, elapsed(start));
        }

        log.info("DirectoryCrawlerService: source={} rawUrls={}", name, rawUrls.size());

        int skippedDuplicate = 0;
        int processed = 0;
        int ok = 0;
        int errors = 0;

        for (String rawUrl : rawUrls) {
            if (processed >= budget) {
                log.info("DirectoryCrawlerService: source={} budget reached, stopping", name);
                break;
            }

            String url = urlNormalizer.normalizeUrl(rawUrl);
            if (url == null) {
                log.debug("DirectoryCrawlerService: skipping malformed url={}", rawUrl);
                continue;
            }

            String domain = urlNormalizer.extractNormalizedDomain(url);

            DiscoveryDuplicateChecker.SeenDecision decision =
                    duplicateChecker.checkAlreadySeen(url, domain);

            if (decision != DiscoveryDuplicateChecker.SeenDecision.NOT_SEEN) {
                log.debug("DirectoryCrawlerService: skipping url={} ({})", url, decision);
                skippedDuplicate++;
                continue;
            }

            processed++;

            try {
                farmScraperService.scrapeFarmLeads(url);

                // zapisz URL do bazy — bez tego dedup nie zadziała przy następnym runie
                FarmClassificationResult directoryResult = new FarmClassificationResult(
                        true, false, "directory:" + name, null
                );
                discoveredUrlWriter.save(url, directoryResult);

                ok++;
                log.debug("DirectoryCrawlerService: scraped ok url={}", url);
            } catch (Exception e) {
                errors++;
                log.warn("DirectoryCrawlerService: scrape failed url={}: {}", url, e.getMessage());
            }
        }

        long durationMs = elapsed(start);
        log.info("DirectoryCrawlerService: source={} done. fetched={}, skippedDuplicate={}, processed={}, ok={}, errors={}, durationMs={}",
                name, rawUrls.size(), skippedDuplicate, processed, ok, errors, durationMs);

        return new DirectoryCrawlResult(name, rawUrls.size(), skippedDuplicate, processed, ok, errors, durationMs);
    }

    private long elapsed(Instant start) {
        return Duration.between(start, Instant.now()).toMillis();
    }
}