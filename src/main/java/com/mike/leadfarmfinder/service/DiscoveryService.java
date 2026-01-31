package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.config.LeadFinderProperties;
import com.mike.leadfarmfinder.dto.FarmClassificationResult;
import com.mike.leadfarmfinder.entity.DiscoveryRunStats;
import com.mike.leadfarmfinder.entity.SerpQueryCursor;
import com.mike.leadfarmfinder.repository.DiscoveredUrlRepository;
import com.mike.leadfarmfinder.repository.DiscoveryRunStatsRepository;
import com.mike.leadfarmfinder.repository.SerpQueryCursorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
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

    private static final Set<String> BLOCKED_DOMAINS = Set.of(
            "de.indeed.com",
            "facebook.com",
            "indeed.com",
            "instagram.com",
            "linkedin.com",
            "meinestadt.de",
            "stepstone.de",
            "tiktok.com",
            "xing.com",
            "youtube.com",

            "sh-tourismus.de",

            "ard.de",
            "br.de",
            "hr.de",
            "mdr.de",
            "ndr.de",
            "rtl.de",
            "swr.de",
            "wdr.de",
            "zdf.de",

            "airbnb.com",
            "airbnb.de",
            "booking.com",
            "kleinanzeigen.de",

            "obstbaufachbetriebe.de"
    );

    private static final List<String> FARM_KEYWORDS = List.of(
            "hof", "hofladen",
            "obst", "obsthof",
            "gemuese", "gemüse",
            "erdbeer", "beeren", "himbeer", "beerenhof",
            "spargel",
            "kartoffel", "kartoffelhof",
            "ackerbau", "getreide", "getreidehof",
            "landwirtschaft", "bauernhof", "landhof",
            "gaertnerei", "gärtnerei", "gartenbau",
            "blumen", "schnittblumen",
            "weingut", "winzer",
            "biohof", "bioland", "demeter",

            // zima / całoroczne
            "gewaechshaus", "gewächshaus",
            "jungpflanzen", "pflanzen",
            "baumschule", "stauden",
            "pilz", "pilze", "pilzzucht", "champignon",
            "gemuesebau", "gemüsebau",
            "garten",
            "betrieb", "familienbetrieb",
            "direktvermarktung", "hofverkauf",
            "verarbeitung", "aufbereitung", "pack", "verpackung", "sortierung", "lager",
            "milchvieh", "vieh", "rinder", "schwein", "gefluegel", "geflügel", "eier", "legehennen"
    );

    // twarde negatywy w PATH (tanie odfiltrowanie content/spam przed OpenAI)
    private static final List<String> HARD_NEGATIVE_PATH_TOKENS = List.of(
            "/ratgeber",
            "/blog",
            "/magazin",
            "/news",
            "/presse",
            "/artikel",
            "/article",
            "/report",
            "/wiki",
            "/lexikon",
            "/kategorie",
            "/category",
            "/tag/",
            "/tags/",
            "/author/",
            "/job", "/jobs", "/stellen", "/karriere",

            // ✅ portale / listy / mapy / turystyka (wysoka szansa "listing many farms")
            "/anbieter",
            "/anbieterverzeichnis",
            "/verzeichnis",
            "/liste",
            "/listen",
            "/uebersicht",
            "/übersicht",
            "/karte",
            "/map",
            "/region",
            "/tourismus",
            "/urlaub",
            "/freizeit"
    );

    /**
     * HARD NEGATIVE = coś, co niemal na pewno nie jest farmą (instytucje, media, katalogi, turystyka, pośrednictwo pracy).
     * Usuwamy z hard-negative "wordpress/wix/ionos/jimdo..." bo to nie jest sygnał "nie-farma".
     */
    private static final List<String> HARD_NEGATIVE_KEYWORDS = List.of(
            // polityka / administracja / urzędy
            "bundeskanzler",
            "bundesregierung",
            "ministerium",
            "regierung",
            "landtag",
            "verwaltung",
            "rathaus",
            "stadt-",
            "gemeinde-",
            "kreis-",
            "landkreis",

            // statystyka/uczelnie
            "destatis",
            "statista",
            "statistik",
            "hochschule",
            "uni-",
            "universitaet",
            "universität",
            "fh-",

            // NGO / instytucje publiczne / EU
            "greenpeace.",
            "nabu.",
            "wwf.",
            "verbraucherzentrale",
            "verbraucherzentralen",
            "ec.europa",
            "europa.eu",

            // instytucje branżowe / izby / urzędy rolnicze (często katalogi/porady, nie lead)
            "bauernverband",
            "ble.de",
            "bzfe.de",
            "handelskammer",
            "kammer",
            "landwirtschaft-bw.de",
            "lwk-niedersachsen.de",

            // media / news
            "dw.com",
            "deutschlandfunk",
            "deutsche-welle",
            "faz",
            "focus",
            "merkur",
            "morgenpost",
            "rbb",
            "sueddeutsche",
            "spiegel",
            "stern",
            "t-online",
            "tagesschau",
            "tagesthemen",
            "welt",
            "zeit",
            "zdf",
            "wdr",
            "swr",
            "ndr",
            "mdr",
            "hr",
            "br",
            "ard",
            "bild",

            // travel/tourism (często "hof" w kontekście urlopu, nie prac sezonowych)
            "tourism",
            "tourismus",
            "touristik",
            "reiseland",
            "reisefuhrer",
            "reiseführer",
            "urlaub",
            "visit",
            "freizeit",
            "ausflug",
            "erleben",
            "stadtmarketing",

            // katalogi / agregatory / marketplace
            "branchenbuch",
            "gelbeseiten",
            "marktplatz",
            "verzeichnis",
            "portal",
            "cylex",
            "golocal",
            "yelp",
            "11880",
            "trustedshops",
            "werliefertwas",

            // noclegi (wycinamy "Ferienhof" itd.)
            "airbnb",
            "booking",
            "ferienwohnung",
            "ferienwohnungen",
            "ferienhof",
            "bauernhofurlaub",
            "urlaub-auf-dem-bauernhof",
            "ferienhaus",
            "ferienhaeuser",
            "ferienhäuser",
            "pension",
            "gasthof",
            "hotel",
            "zimmer",
            "zimmervermietung",
            "camping",
            "zeltplatz",
            "stellplatz",
            "wohnmobil",
            "glamping",
            "tiny-house",
            "tiny-house-dorf",
            "wellness",
            "sauna",
            "spa",

            // pośrednictwo / HR
            "zeitarbeit",
            "zeitarbeitsfirma",
            "personalvermittlung",
            "personaldienstleister",
            "arbeitsagentur",
            "jobvermittlung",
            "leiharbeit",
            "arbeitnehmerüberlassung",

            // domeny/specyficzne wykluczenia
            "obstbaufachbetriebe"
    );

    /**
     * SOFT NEGATIVE = podejrzane, ale nie blokujemy (tylko -score).
     * Tu lądują typowe hosting/cms/szyldy, bo farmy często tego używają.
     */
    private static final List<String> SOFT_NEGATIVE_DOMAIN_TOKENS = List.of(
            "wordpress",
            "wix",
            "jimdo",
            "ionos",
            "strato",
            "webnode",
            "joomla",
            "hosting"
    );

    // odrzucamy pliki zanim w ogóle pójdą dalej
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            ".pdf",
            ".jpg", ".jpeg", ".png", ".gif", ".webp",
            ".zip", ".rar", ".7z",
            ".doc", ".docx",
            ".xls", ".xlsx", ".ods",
            ".ppt", ".pptx",
            ".csv", ".txt", ".xml"
    );

    // hinty w URL (kontakt/impressum/itd.)
    private static final List<String> URL_HINT_KEYWORDS = List.of(
            "/kontakt", "/contact",
            "/impressum",
            "/datenschutz",
            "/ueber-uns", "/uber-uns", "/über-uns",
            "/betrieb", "/unternehmen",
            "/hofladen", "/hofverkauf"
    );

    /**
     * Automatyczne "minusy" do query, żeby Serp/Google mniej zwracał katalogów i sociali.
     * To jest proste i bardzo skuteczne.
     */
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

    private int queryIndex = 0;

    /**
     * NOWE ZACHOWANIE (po refaktorze):
     * - nie zawijamy currentPage do 1
     * - gdy przekroczymy maxPage → ustawiamy currentPage = maxPage + 1 (sentinel "DONE")
     * - wybór query pomija DONE
     * - jeśli wszystkie są DONE → zwracamy pustą listę i discovery "nie działa"
     *
     * Dodatkowo (KLUCZOWE na wolumen maili):
     * ✅ jeśli nie mamy treści HTML (snippet pusty / za krótki / unsupported mime / timeout):
     *    - NIE wysyłamy do OpenAI
     *    - NIE zapisujemy farm=false do DB (żeby nie spalić domeny na zawsze przez existsByDomain)
     */
    public List<String> findCandidateFarmUrls(int limit) {

        int skippedAlreadySeenDomain = 0;
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

        // dopinamy minusy do query (bez zmiany cursora; cursor jest per rawQuery)
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
            log.info("DiscoveryService: picked query is already exhausted (DONE). query='{}', currentPage={}, maxPage={}",
                    rawQuery, startPage, maxPage);
            return List.of();
        }

        log.info("DiscoveryService: starting SERP from page={} (maxPage={}) for query='{}'",
                startPage, maxPage, rawQuery);

        List<String> accepted = new ArrayList<>();

        int pagesVisited = 0;
        int rawUrlsTotal = 0;
        int cleanedUrlsTotal = 0;
        int filteredAsAlreadyDiscovered = 0;
        int acceptedCount = 0;
        int rejectedCount = 0;
        int errorsCount = 0;

        int consecutiveEmptyNewUrls = 0;

        // było 2, teraz 4 (bardziej realistyczne)
        int baseEarlyExitAfterEmptyPages = 4;

        for (int i = 0; i < maxPagesPerRun && accepted.size() < limit; i++) {

            // lekko adaptacyjnie: jak jesteś już głęboko, 2 puste strony pod rząd wystarczą, żeby iść dalej
            int earlyExitAfterEmptyPages = (currentPage >= 6) ? 2 : baseEarlyExitAfterEmptyPages;

            log.info("DiscoveryService: fetching SERP page={} (runPageIndex={}) for query='{}'",
                    currentPage, i, rawQuery);

            List<String> rawUrls = serpApiService.searchUrls(query, resultsPerPage, currentPage);
            rawUrlsTotal += rawUrls.size();

            log.info("DiscoveryService: raw urls from SerpAPI (page={}) = {}", currentPage, rawUrls.size());

            if (rawUrls.isEmpty()) {
                log.info("DiscoveryService: no more results from SerpAPI for page={}, moving to next page", currentPage);
                currentPage = advancePageOrExhaust(currentPage, maxPage);
                break;
            }

            pagesVisited++;

            List<String> cleaned = rawUrls.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .filter(this::isNotFileUrl)
                    .filter(this::isAllowedDomain)
                    .distinct()
                    .collect(Collectors.toList());

            cleanedUrlsTotal += cleaned.size();

            log.info("DiscoveryService: urls after domain filter (page={}) = {}", currentPage, cleaned.size());

            List<String> newUrlsOnly = new ArrayList<>();
            Set<String> normalizedSeenThisPage = new HashSet<>();

            for (String url : cleaned) {
                if (accepted.size() >= limit) break;

                String normalized = normalizeUrl(url);

                if (!normalized.equals(url)) {
                    normalizedChanged++;
                }

                if (!normalizedSeenThisPage.add(normalized)) {
                    continue;
                }

                String domain = extractNormalizedDomain(normalized);
                if (domain == null || domain.isBlank()) {
                    continue;
                }

                // ✅ zostaje: jak domena była już widziana (nawet not-farm) -> nie wracamy do niej
                SeenDecision seen = checkAlreadySeen(normalized, domain);
                if (seen != SeenDecision.NOT_SEEN) {
                    filteredAsAlreadyDiscovered++;
                    if (seen == SeenDecision.SEEN_BY_DOMAIN) skippedAlreadySeenDomain++;
                    else alreadySeenSkipped++;
                    continue;
                }

                // ✅ hard-negative path: SKIP bez zapisu do DB (żeby nie palić domeny na zawsze)
                if (isHardNegativePath(normalized)) {
                    rejectedCount++;
                    log.info("DiscoveryService: SKIP (hard-negative-path - no DB save) url={}", normalized);
                    continue;
                }

                newUrlsOnly.add(normalized);
            }

            openAiCandidates += newUrlsOnly.size();

            log.info("DiscoveryService: new urls for OpenAI after discovered filter (page={}) = {}",
                    currentPage, newUrlsOnly.size());

            if (newUrlsOnly.isEmpty()) {
                consecutiveEmptyNewUrls++;

                log.info("DiscoveryService: empty NEW candidates streak = {} (page={})",
                        consecutiveEmptyNewUrls, currentPage);

                if (consecutiveEmptyNewUrls >= earlyExitAfterEmptyPages) {
                    log.info("DiscoveryService: early-exit after {} consecutive empty pages (threshold={}). query='{}', pageNow={}, pagesVisitedSoFar={}",
                            consecutiveEmptyNewUrls, earlyExitAfterEmptyPages, rawQuery, currentPage, pagesVisited);

                    currentPage = advancePageOrExhaust(currentPage, maxPage);
                    break;
                }

                currentPage = advancePageOrExhaust(currentPage, maxPage);
                if (currentPage > maxPage) {
                    break;
                }
                continue;
            } else {
                consecutiveEmptyNewUrls = 0;
            }

            List<ScoredUrl> scored = newUrlsOnly.stream()
                    .map(u -> new ScoredUrl(u, computeDomainPriorityScore(u)))
                    .sorted(Comparator.comparingInt(ScoredUrl::score).reversed())
                    .toList();

            log.info("DiscoveryService: scored {} NEW urls (top example: {})",
                    scored.size(),
                    scored.isEmpty() ? "none" : (scored.get(0).url() + " score=" + scored.get(0).score())
            );

            for (ScoredUrl scoredUrl : scored) {

                if (accepted.size() >= limit) break;

                String url = scoredUrl.url();

                try {
                    String snippet = fetchTextSnippet(url);

                    // ✅ HARD RULE: bez treści nie klasyfikujemy i nie zapisujemy (żeby nie spalić domeny)
                    if (snippet == null || snippet.isBlank()) {
                        rejectedCount++;
                        log.info("DiscoveryService: SKIP (empty/low-quality snippet - no OpenAI, no DB save) url={} score={}",
                                url, scoredUrl.score());
                        continue;
                    }

                    FarmClassificationResult result = farmClassifier.classifyFarm(url, snippet);
                    saveDiscoveredUrl(url, result);

                    if (result.isFarm()) {
                        String finalUrl = result.mainContactUrl() != null ? result.mainContactUrl() : url;
                        accepted.add(finalUrl);

                        log.info(
                                "DiscoveryService: ACCEPTED (FARM) url={} score={} seasonalJobs={} reason={}",
                                finalUrl, scoredUrl.score(), result.isSeasonalJobs(), result.reason()
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
                    log.warn("DiscoveryService: error for url={} (score={}): {}",
                            url, scoredUrl.score(), e.getMessage());
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

        acceptedCount = distinctAccepted.size();

        DiscoveryRunStats stats = new DiscoveryRunStats();
        stats.setQuery(rawQuery);
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
                "DiscoveryService: returning {} accepted urls (query='{}', startPage={}, endPage={}, done={}, pagesVisited={}, skippedAlreadySeenDomain={}, alreadySeenSkippedUrl={}, openAiCandidates={}, normalizedChanged={})",
                distinctAccepted.size(), rawQuery, startPage, currentPage, (currentPage > maxPage), pagesVisited,
                skippedAlreadySeenDomain, alreadySeenSkipped, openAiCandidates, normalizedChanged
        );

        return distinctAccepted;
    }

    // ===== NEW helpers (DONE / selection) =====

    private boolean isExhausted(SerpQueryCursor c) {
        return c.getCurrentPage() > c.getMaxPage();
    }

    private int advancePageOrExhaust(int currentPage, int maxPage) {
        int next = currentPage + 1;
        if (next > maxPage) {
            return maxPage + 1; // DONE
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

    // ===== Query negatives =====

    private String withQueryNegatives(String rawQuery) {
        String q = rawQuery == null ? "" : rawQuery.trim();
        if (q.isBlank()) return q;

        // jeśli już ktoś ręcznie dopisał minusy, nie dublujemy agresywnie
        String lower = q.toLowerCase(Locale.ROOT);
        boolean hasAnyMinus = lower.contains(" -branchenbuch")
                || lower.contains(" -gelbeseiten")
                || lower.contains(" -11880")
                || lower.contains(" -cylex");

        if (hasAnyMinus) return q;

        StringBuilder sb = new StringBuilder(q);
        for (String neg : QUERY_NEGATIVE_TOKENS) {
            sb.append(' ').append(neg);
        }
        return sb.toString();
    }

    // ===== rest unchanged =====

    private boolean isHardNegativePath(String url) {
        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            if (path == null) return false;

            String p = path.toLowerCase(Locale.ROOT);

            for (String token : HARD_NEGATIVE_PATH_TOKENS) {
                if (p.contains(token)) {
                    return true;
                }
            }
            return false;

        } catch (Exception e) {
            return false;
        }
    }

    private String normalizeUrl(String url) {
        try {
            URI uri = new URI(url.trim());

            String scheme = (uri.getScheme() == null) ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);

            String host = uri.getHost();
            if (host == null) return url.trim();

            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) host = host.substring(4);

            String path = uri.getPath();
            if (path == null || path.isBlank()) path = "/";

            String p = path.toLowerCase(Locale.ROOT);
            if (p.endsWith("/index.html") || p.endsWith("/index.htm")) {
                path = path.substring(0, path.lastIndexOf("/index."));
                if (path.isBlank()) path = "/";
            }

            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            return new URI(scheme, host, path, null).toString();

        } catch (Exception e) {
            return url.trim();
        }
    }

    private SeenDecision checkAlreadySeen(String normalizedUrl, String domain) {
        if (discoveredUrlRepository.existsByUrl(normalizedUrl)) {
            return SeenDecision.SEEN_BY_URL;
        }
        // ✅ zostaje twardo: domena raz widziana = skip (nie robimy drugich podejść)
        if (domain != null && !domain.isBlank() && discoveredUrlRepository.existsByDomain(domain)) {
            return SeenDecision.SEEN_BY_DOMAIN;
        }
        return SeenDecision.NOT_SEEN;
    }

    private enum SeenDecision {
        NOT_SEEN,
        SEEN_BY_DOMAIN,
        SEEN_BY_URL
    }

    private String extractNormalizedDomain(String url) {
        String domain = extractDomain(url);
        if (domain == null) return null;
        return domain.toLowerCase(Locale.ROOT).trim();
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

            boolean isNew = (entity.getId() == null);

            entity.setUrl(url);
            entity.setDomain(extractNormalizedDomain(url));
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
        String d = extractNormalizedDomain(url);
        if (d == null) {
            log.info("DiscoveryService: dropping url={} (no domain)", url);
            return false;
        }

        if (BLOCKED_DOMAINS.contains(d)) {
            log.info("DiscoveryService: dropping url={} (blocked domain={})", url, d);
            return false;
        }

        if (isHardNegative(d)) {
            log.info("DiscoveryService: dropping url={} (hard-negative domain={})", url, d);
            return false;
        }

        if (d.contains("zeitung") || d.contains("news")) {
            log.info("DiscoveryService: dropping url={} (looks like news/media domain={})", url, d);
            return false;
        }

        if (!looksLikeFarmDomain(d)) {
            log.debug("DiscoveryService: domain not farm-looking (soft allow), keeping for OpenAI: url={} domain={}", url, d);
        }

        return true;
    }

    private boolean looksLikeFarmDomain(String domain) {
        String d = domain.toLowerCase(Locale.ROOT);
        if (isHardNegative(d)) return false;
        return hasFarmKeyword(d);
    }

    private boolean hasFarmKeyword(String d) {
        for (String kw : FARM_KEYWORDS) {
            if (d.contains(kw)) return true;
        }
        return false;
    }

    private int computeDomainPriorityScore(String url) {
        String d = extractNormalizedDomain(url);
        if (d == null) return 0;

        if (isHardNegative(d)) return -100;

        int score = 0;

        // baza: keywordy w domenie
        for (String kw : FARM_KEYWORDS) {
            if (d.contains(kw)) score += 20;
        }

        // lokalność
        if (d.endsWith(".de")) score += 10;

        // krótsze domeny lekko na plus
        if (d.length() <= 15) score += 5;

        // podejrzane tokeny domeny
        if (d.contains("shop") || d.contains("markt") || d.contains("portal")) score -= 5;

        // SOFT negatives: tylko -score, nie blokada
        for (String soft : SOFT_NEGATIVE_DOMAIN_TOKENS) {
            if (d.contains(soft)) {
                score -= 3;
            }
        }

        // boost hintów URL ZAWSZE (niezależnie od domeny)
        String u = url.toLowerCase(Locale.ROOT);
        for (String hint : URL_HINT_KEYWORDS) {
            if (u.contains(hint)) {
                score += 8;
            }
        }

        return score;
    }

    private boolean isHardNegative(String text) {
        String d = text.toLowerCase(Locale.ROOT);
        for (String bad : HARD_NEGATIVE_KEYWORDS) {
            if (d.contains(bad)) return true;
        }
        return false;
    }

    private record ScoredUrl(String url, int score) {}

    private String extractDomain(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null) return null;

            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) host = host.substring(4);
            return host;
        } catch (Exception e) {
            log.warn("DiscoveryService: failed to extract domain from {}: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * Snippet tylko z HTML. Jeśli treść jest za krótka / nie-HTML / błąd -> zwracamy "".
     * (Wyżej: "" => SKIP bez OpenAI i bez zapisu do DB)
     */
    private String fetchTextSnippet(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; LeadFarmFinderBot/1.0)")
                    .timeout(10_000)
                    .followRedirects(true)
                    .get();

            // usuń boilerplate
            doc.select("script,style,noscript").remove();

            String text = doc.text();
            if (text == null) return "";

            text = text.trim();

            // minimalny próg jakości: jak to ma 100–150 znaków, OpenAI i tak zgaduje
            if (text.length() < 200) return "";

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

    private boolean isNotFileUrl(String url) {
        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            if (path == null) return true;

            String p = path.toLowerCase(Locale.ROOT);
            for (String ext : BLOCKED_EXTENSIONS) {
                if (p.endsWith(ext)) {
                    log.info("DiscoveryService: dropping url={} (blocked file extension={})", url, ext);
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.info("DiscoveryService: dropping url={} (invalid url for file-check: {})", url, e.getMessage());
            return false;
        }
    }
}
