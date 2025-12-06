package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.config.LeadFinderProperties;
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
    private final LeadFinderProperties leadFinderProperties;

    /**
     * Twardo blokowane domeny – jeśli dokładnie taka domena się pojawi, nie przechodzi dalej.
     * (duże portale, social media, job-boardy, media)
     */
    private static final Set<String> BLOCKED_DOMAINS = Set.of(
            "indeed.com",
            "de.indeed.com",
            "instagram.com",
            "facebook.com",
            "m.facebook.com",
            "youtube.com",
            "linkedin.com",
            "tiktok.com",
            "xing.com",
            "stepstone.de",
            "meinestadt.de",

            // portale turystyczne / regiony
            "sh-tourismus.de",

            // przykładowe duże media
            "swr.de",
            "zdf.de",
            "ard.de",
            "rtl.de",
            "ndr.de",
            "wdr.de",
            "mdr.de",
            "br.de",
            "hr.de"
    );

    /**
     * Słowa kluczowe charakterystyczne dla gospodarstw – używane w scoringu i heurystyce domen.
     */
    private static final List<String> FARM_KEYWORDS = List.of(
            "hof", "hofladen",
            "obst", "gemuese", "gemüse",
            "erdbeer", "beeren",
            "spargel",
            "bauern", "landwirtschaft",
            "bioland", "demeter", "biohof",
            "weingut", "winzer", "obsthof"
    );

    /**
     * Hard-negative – jeśli domena / URL zawiera któreś z tych słów,
     * traktujemy to jako "na pewno nie farma" (media, turystyka, administracja itd.).
     *
     * Używamy tego w:
     *  - looksLikeFarmDomain()  -> natychmiastowe odrzucenie
     *  - isAllowedDomain()      -> dodatkowy filtr
     *  - computeDomainPriorityScore() -> score -100 jako dodatkowe zabezpieczenie
     */
    private static final List<String> HARD_NEGATIVE_KEYWORDS = List.of(
            // Rząd / administracja
            "bundesregierung", "bundeskanzler", "ministerium",
            "regierung", "landtag", "verwaltung",

            // Samorządy / publiczne instytucje
            "stadt-", "kreis-", "landkreis", "gemeinde-", "rathaus",

            // Statystyka / nauka
            "destatis", "statistik", "statista",
            "hochschule", "universitaet", "universität", "uni-", "fh-",

            // Organizacje / NGO
            "verbraucherzentrale", "verbraucherzentralen",
            "nabu.", "wwf.", "greenpeace.",

            // Strony UE
            "europa.eu", "ec.europa",

            // Izby rolnicze i państwowe instytucje rolnicze
            "kammer", "handelskammer", "bauernverband",
            "landwirtschaft-bw.de", "lwk-niedersachsen.de",
            "ble.de", "bzfe.de",

            // MEDIA / TV / RADIO / PRESS
            "swr", "ard", "zdf", "ndr", "wdr", "mdr", "rbb",
            "spiegel", "focus", "stern", "welt", "bild",
            "t-online", "faz", "zeit", "tagesschau", "tagesthemen",
            "merkur", "sueddeutsche", "morgenpost",
            "deutschlandfunk", "dw.com", "deutsche-welle",

            // NEWS / PRESS KEYWORDS
            "nachrichten", "news", "presse", "press", "report",
            "journal", "zeitung", "gazette", "magazin", "blog",

            // Turystyka / portale regionalne / lifestyle
            "tourismus", "tourism", "touristik",
            "reisefuhrer", "reiseführer", "urlaub",
            "reiseland", "ausflug", "freizeit",
            "stadtmarketing", "messe", "visit",
            "erleben", "anzeiger", "kurier",

            // Katalogi branżowe / generatory stron / portale
            "branchenbuch", "gelbeseiten", "verzeichnis",
            "portal", "marktplatz",

            // CMS / "site builders"
            "jimdo", "wix", "wordpress", "joomla",
            "webnode", "strato", "ionos",

            // Agencje i usługi
            "marketing", "agentur", "consulting",
            "fotografie", "werbung",
            "seo", "webdesign", "hosting",

            // FILM / TV / VIDEO / STREAM / MEDIA
            "tv", "video", "stream", "media"
    );

    // prosty in-memory index do rotacji zapytań
    private int queryIndex = 0;

    /**
     * Znajdź kandydackie URLe gospodarstw:
     * 1) SerpAPI -> kilka stron SERP (z rotacją queries)
     * 2) filtr po domenie (BLOCKED_DOMAINS + hard-negative + heurystyka)
     * 3) REUSE discovered_urls:
     *      - jeśli already farm -> ACCEPT bez OpenAI
     *      - jeśli already not farm -> REJECT bez OpenAI
     * 4) dla nowych: scoring domeny + OpenAI classifier (is_farm == true)
     * 5) zapis statystyk do discovery_run_stats
     * 6) zapis każdego sklasyfikowanego URL do discovered_urls
     */
    public List<String> findCandidateFarmUrls(int limit) {

        int resultsPerPage = leadFinderProperties.getDiscovery().getResultsPerPage();
        int maxPagesPerRun = leadFinderProperties.getDiscovery().getMaxPagesPerRun();

        List<String> queries = leadFinderProperties.getDiscovery().getQueries();
        if (queries == null || queries.isEmpty()) {
            throw new IllegalStateException("Discovery queries are not configured! Add leadfinder.discovery.queries[]");
        }

        // wybór aktualnego zapytania + rotacja indexu
        int currentQueryIndex = queryIndex;
        String query = queries.get(currentQueryIndex);
        queryIndex = (queryIndex + 1) % queries.size();

        LocalDateTime startedAt = LocalDateTime.now();

        log.info(
                "DiscoveryService: searching farms for query='{}' (queryIndex={}), limit={}, resultsPerPage={}, maxPagesPerRun={}",
                query, currentQueryIndex, limit, resultsPerPage, maxPagesPerRun
        );

        SerpQueryCursor cursor = loadOrCreateCursor(query);
        int startPage = cursor.getCurrentPage();
        int currentPage = startPage;
        int maxPage = cursor.getMaxPage();

        log.info("DiscoveryService: starting SERP from page={} (maxPage={}) for query='{}'",
                startPage, maxPage, query);

        List<String> accepted = new ArrayList<>();

        // statystyki
        int pagesVisited = 0;
        int rawUrlsTotal = 0;
        int cleanedUrlsTotal = 0;
        int filteredAsAlreadyDiscovered = 0;
        int acceptedCount = 0;
        int rejectedCount = 0;
        int errorsCount = 0;

        for (int i = 0; i < maxPagesPerRun && accepted.size() < limit; i++) {
            log.info("DiscoveryService: fetching SERP page={} (runPageIndex={}) for query='{}'",
                    currentPage, i, query);

            List<String> rawUrls = serpApiService.searchUrls(query, resultsPerPage, currentPage);
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

            // 2) REUSE discovered_urls:
            List<String> newUrlsOnly = new ArrayList<>();

            for (String url : cleaned) {
                if (accepted.size() >= limit) {
                    break;
                }

                Optional<DiscoveredUrl> existingOpt = discoveredUrlRepository.findByUrl(url);
                if (existingOpt.isEmpty()) {
                    newUrlsOnly.add(url);
                    continue;
                }

                filteredAsAlreadyDiscovered++;

                DiscoveredUrl existing = existingOpt.get();
                boolean farm = Boolean.TRUE.equals(existing.isFarm());

                if (farm) {
                    accepted.add(url);
                    acceptedCount++;
                    log.info("DiscoveryService: REUSED (FARM) url={} from discovered_urls, skipping OpenAI", url);
                } else {
                    rejectedCount++;
                    log.info("DiscoveryService: REUSED (NOT FARM) url={} from discovered_urls, skipping OpenAI", url);
                }
            }

            log.info("DiscoveryService: new urls for OpenAI after discovered filter (page={}) = {}",
                    currentPage, newUrlsOnly.size());

            // 3) scoring i sortowanie NOWYCH url-i
            List<ScoredUrl> scored = newUrlsOnly.stream()
                    .map(u -> new ScoredUrl(u, computeDomainPriorityScore(u)))
                    .sorted(Comparator.comparingInt(ScoredUrl::score).reversed())
                    .toList();

            log.info("DiscoveryService: scored {} NEW urls (top example: {})",
                    scored.size(),
                    scored.isEmpty() ? "none"
                            : (scored.get(0).url() + " score=" + scored.get(0).score())
            );

            // 4) OpenAI classifier na posortowanych nowych url-ach
            for (ScoredUrl scoredUrl : scored) {
                if (accepted.size() >= limit) {
                    break;
                }

                String url = scoredUrl.url();

                try {
                    String snippet = fetchTextSnippet(url);
                    if (snippet.isBlank()) {
                        log.info("DiscoveryService: empty snippet for url={} (score={}), using URL as fallback snippet",
                                url, scoredUrl.score());
                        snippet = url;
                    }

                    FarmClassificationResult result = farmClassifier.classifyFarm(url, snippet);

                    // zapis do discovered_urls – niezależnie od wyniku
                    saveDiscoveredUrl(url, result);

                    if (result.isFarm()) {

                        String finalUrl = result.mainContactUrl() != null
                                ? result.mainContactUrl()
                                : url;

                        accepted.add(finalUrl);
                        acceptedCount++;

                        log.info(
                                "DiscoveryService: ACCEPTED (FARM) url={} score={} seasonalJobs={} reason={}",
                                finalUrl,
                                scoredUrl.score(),
                                result.isSeasonalJobs(),
                                result.reason()
                        );

                    } else {
                        rejectedCount++;
                        log.info(
                                "DiscoveryService: REJECTED (NOT A FARM) url={} score={} seasonalJobs={} reason={}",
                                url,
                                scoredUrl.score(),
                                result.isSeasonalJobs(),
                                result.reason()
                        );
                    }

                } catch (Exception e) {
                    errorsCount++;
                    log.warn("DiscoveryService: error for url={} (score={}): {}",
                            url, scoredUrl.score(), e.getMessage());
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
                "DiscoveryService: returning {} accepted urls (query='{}', startPage={}, endPage={}, pagesVisited={}, reusedFromDiscovered={}, distinctAccepted={})",
                distinctAccepted.size(), query, startPage, currentPage, pagesVisited,
                filteredAsAlreadyDiscovered, distinctAccepted.size()
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
                    cursor.setMaxPage(leadFinderProperties.getDiscovery().getDefaultMaxSerpPage());
                    cursor.setLastRunAt(null);
                    SerpQueryCursor saved = serpQueryCursorRepository.save(cursor);
                    log.info("DiscoveryService: created new SERP cursor for query='{}'", query);
                    return saved;
                });
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

    /**
     * Główny filtr domen:
     *  - musi mieć host
     *  - nie może być na BLOCKED_DOMAINS
     *  - nie może być hard-negative (media, administracja, turystyka, portale itd.)
     *  - + heurystyka looksLikeFarmDomain (musi wyglądać na gospodarstwo)
     */
    private boolean isAllowedDomain(String url) {
        String domain = extractDomain(url);
        if (domain == null) {
            log.info("DiscoveryService: dropping url={} (no domain)", url);
            return false;
        }

        String d = domain.toLowerCase(Locale.ROOT);

        if (BLOCKED_DOMAINS.contains(d)) {
            log.info("DiscoveryService: dropping url={} (blocked domain={})", url, domain);
            return false;
        }

        if (isHardNegative(d)) {
            log.info("DiscoveryService: dropping url={} (hard-negative domain={})", url, domain);
            return false;
        }

        // dodatkowe bezpieczeństwo na media/news (gdyby coś się prześlizgnęło)
        if (d.contains("zeitung") || d.contains("news")) {
            log.info("DiscoveryService: dropping url={} (looks like news/media domain={})", url, domain);
            return false;
        }

        if (!looksLikeFarmDomain(domain)) {
            log.info("DiscoveryService: dropping url={} (domain does not look farm-related: {})", url, domain);
            return false;
        }

        return true;
    }

    /**
     * Heurystyka: domena „wygląda” na coś związanego z farmami.
     *
     * W tej ostrej wersji:
     *  - jeśli domain zawiera cokolwiek z HARD_NEGATIVE_KEYWORDS -> false
     *  - jeśli NIE zawiera żadnego słowa z FARM_KEYWORDS -> false
     *  - tylko domeny z "hof/obst/spargel/erdbeer/bauern/weingut/..." przechodzą.
     */
    private boolean looksLikeFarmDomain(String domain) {
        String d = domain.toLowerCase(Locale.ROOT);

        if (isHardNegative(d)) {
            return false;
        }

        boolean looksFarmy = FARM_KEYWORDS.stream().anyMatch(d::contains);
        if (!looksFarmy) {
            return false;
        }

        log.debug("DiscoveryService: domain={} looks farm-related by keyword heuristic", domain);
        return true;
    }

    /**
     * LF-6.3 – oblicza priorytet domeny na podstawie heurystyk.
     * Im wyższy score, tym wcześniej URL idzie do OpenAI.
     */
    private int computeDomainPriorityScore(String url) {
        String domain = extractDomain(url);
        if (domain == null) {
            return 0;
        }

        String d = domain.toLowerCase(Locale.ROOT);

        // jeśli hard-negative -> praktycznie wykluczamy ten URL ze smart-kolejki
        if (isHardNegative(d)) {
            return -100;
        }

        int score = 0;

        // +20 za każde „farmowe” słowo kluczowe
        for (String kw : FARM_KEYWORDS) {
            if (d.contains(kw)) {
                score += 20;
            }
        }

        // +10 za domenę .de (lokalność)
        if (d.endsWith(".de")) {
            score += 10;
        }

        // małe bonusy / kary za długość i "dziwność" domeny
        if (d.length() <= 15) {
            score += 5; // krótsze, często brandowe
        }

        if (d.contains("shop") || d.contains("markt") || d.contains("portal")) {
            score -= 5; // sklepy / portale – niekoniecznie gospodarstwo
        }

        return score;
    }

    /**
     * Sprawdzanie "hard-negative" – czy tekst (domena/URL) zawiera
     * cokolwiek z listy HARD_NEGATIVE_KEYWORDS.
     */
    private boolean isHardNegative(String text) {
        String d = text.toLowerCase(Locale.ROOT);
        for (String bad : HARD_NEGATIVE_KEYWORDS) {
            if (d.contains(bad)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Prosty record na potrzeby scoringu.
     */
    private record ScoredUrl(String url, int score) {
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
