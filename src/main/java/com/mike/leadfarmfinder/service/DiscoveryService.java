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

    private static final List<String> HARD_NEGATIVE_KEYWORDS = List.of(
            "bundeskanzler",
            "bundesregierung",
            "ministerium",
            "regierung",
            "landtag",
            "verwaltung",

            "gemeinde-",
            "kreis-",
            "landkreis",
            "rathaus",
            "stadt-",

            "destatis",
            "hochschule",
            "statista",
            "statistik",
            "fh-",
            "uni-",
            "universitaet",
            "universität",

            "greenpeace.",
            "nabu.",
            "verbraucherzentrale",
            "verbraucherzentralen",
            "wwf.",

            "ec.europa",
            "europa.eu",

            "bauernverband",
            "ble.de",
            "bzfe.de",
            "handelskammer",
            "kammer",
            "landwirtschaft-bw.de",
            "lwk-niedersachsen.de",

            "bild",
            "dw.com",
            "deutschlandfunk",
            "deutsche-welle",
            "faz",
            "focus",
            "merkur",
            "mdr",
            "morgenpost",
            "ndr",
            "rbb",
            "sueddeutsche",
            "spiegel",
            "stern",
            "swr",
            "t-online",
            "tagesschau",
            "tagesthemen",
            "welt",
            "wdr",
            "zeit",
            "zdf",

            "blog",
            "gazette",
            "journal",
            "magazin",
            "nachrichten",
            "news",
            "presse",
            "press",
            "report",
            "zeitung",

            "ausflug",
            "erleben",
            "freizeit",
            "messe",
            "reiseland",
            "reisefuhrer",
            "reiseführer",
            "stadtmarketing",
            "tonight",
            "tourism",
            "tourismus",
            "touristik",
            "urlaub",
            "visit",
            "anzeiger",
            "kurier",

            "branchenbuch",
            "gelbeseiten",
            "marktplatz",
            "portal",
            "verzeichnis",

            "ionos",
            "jimdo",
            "joomla",
            "strato",
            "webnode",
            "wix",
            "wordpress",

            "agentur",
            "consulting",
            "fotografie",
            "hosting",
            "marketing",
            "seo",
            "webdesign",
            "werbung",

            "media",
            "stream",
            "tv",
            "video",

            "hannover.de",
            "brandenburg.de",

            "agrobusiness",
            "netzwerk",

            "airbnb",
            "booking",
            "obstbaufachbetriebe",

            "ferienwohnung", "ferienwohnungen",
            "ferienhof", "bauernhofurlaub", "urlaub-auf-dem-bauernhof",
            "ferienhaus", "ferienhaeuser", "ferienhäuser",
            "pension", "gasthof", "hotel", "zimmer", "zimmervermietung",
            "camping", "zeltplatz", "stellplatz", "wohnmobil",
            "glamping", "tiny-house", "tiny-house-dorf",
            "wellness", "sauna", "spa",

            "zeitarbeit", "zeitarbeitsfirma",
            "personalvermittlung", "personaldienstleister",
            "arbeitsagentur", "jobvermittlung",
            "leiharbeit", "arbeitnehmerüberlassung"
    );

    // NEW: odrzucamy pliki zanim w ogóle pójdą dalej
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            ".pdf", ".jpg", ".jpeg", ".png", ".gif", ".webp",
            ".zip", ".rar", ".7z",
            ".doc", ".docx", ".xls", ".xlsx",
            ".ppt", ".pptx"
    );

    // NEW: boost tylko jako "priorytet" dla URL-i na farmowych domenach
    private static final List<String> URL_HINT_KEYWORDS = List.of(
            "/kontakt", "/contact",
            "/impressum",
            "/datenschutz",
            "/ueber-uns", "/uber-uns", "/über-uns",
            "/betrieb", "/unternehmen",
            "/hofladen", "/hofverkauf"
    );

    private int queryIndex = 0;

    public List<String> findCandidateFarmUrls(int limit) {

        int resultsPerPage = leadFinderProperties.getDiscovery().getResultsPerPage();
        int maxPagesPerRun = leadFinderProperties.getDiscovery().getMaxPagesPerRun();

        List<String> queries = leadFinderProperties.getDiscovery().getQueries();
        if (queries == null || queries.isEmpty()) {
            throw new IllegalStateException("Discovery queries are not configured! Add leadfinder.discovery.queries[]");
        }

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
                log.info("DiscoveryService: no more results from SerpAPI for page={}, moving to next page", currentPage);
                currentPage++;
                if (currentPage > maxPage) currentPage = 1;
                break;
            }

            pagesVisited++;

            List<String> cleaned = rawUrls.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .filter(this::isNotFileUrl)  // NEW
                    .filter(this::isAllowedDomain)
                    .distinct()
                    .collect(Collectors.toList());

            cleanedUrlsTotal += cleaned.size();

            log.info("DiscoveryService: urls after domain filter (page={}) = {}", currentPage, cleaned.size());

            List<String> newUrlsOnly = new ArrayList<>();

            for (String url : cleaned) {
                if (accepted.size() >= limit) break;

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
                    if (snippet.isBlank()) {
                        log.info("DiscoveryService: empty snippet for url={} (score={}), using URL as fallback snippet",
                                url, scoredUrl.score());
                        snippet = url;
                    }

                    FarmClassificationResult result = farmClassifier.classifyFarm(url, snippet);
                    saveDiscoveredUrl(url, result);

                    if (result.isFarm()) {
                        String finalUrl = result.mainContactUrl() != null ? result.mainContactUrl() : url;
                        accepted.add(finalUrl);
                        acceptedCount++;

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

            currentPage++;
            if (currentPage > maxPage) currentPage = 1;
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

        String d = domain.toLowerCase(Locale.ROOT);

        if (BLOCKED_DOMAINS.contains(d)) {
            log.info("DiscoveryService: dropping url={} (blocked domain={})", url, domain);
            return false;
        }

        if (isHardNegative(d)) {
            log.info("DiscoveryService: dropping url={} (hard-negative domain={})", url, domain);
            return false;
        }

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
        String domain = extractDomain(url);
        if (domain == null) return 0;

        String d = domain.toLowerCase(Locale.ROOT);
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

        // podejrzane tokeny
        if (d.contains("shop") || d.contains("markt") || d.contains("portal")) score -= 5;

        // NEW: boost tylko jeśli domena już jest "farmowa"
        if (hasFarmKeyword(d)) {
            String u = url.toLowerCase(Locale.ROOT);
            for (String hint : URL_HINT_KEYWORDS) {
                if (u.contains(hint)) {
                    score += 8;
                }
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

    private String fetchTextSnippet(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; LeadFarmFinderBot/1.0)")
                    .timeout(10_000)
                    .get();

            String text = doc.text();
            if (text == null) return "";

            int maxLen = 2000;
            return text.length() > maxLen ? text.substring(0, maxLen) : text;
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
