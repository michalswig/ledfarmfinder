package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.config.LeadFinderProperties;
import com.mike.leadfarmfinder.entity.FarmLead;
import com.mike.leadfarmfinder.entity.FarmSource;
import com.mike.leadfarmfinder.repository.FarmLeadRepository;
import com.mike.leadfarmfinder.repository.FarmSourceRepository;
import com.mike.leadfarmfinder.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FarmScraperService {

    private final FarmLeadRepository repository;
    private final EmailExtractor emailExtractor;
    private final DomainCrawler domainCrawler;
    private final FarmSourceRepository farmSourceRepository;
    private final LeadFinderProperties leadFinderProperties;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/129.0.0.0 Safari/537.36";

    private static final String REFERRER = "https://www.google.com";

    // NEW: standardowe ścieżki, gdy kontaktUrl jest 404 / crawler nic nie zwróci
    private static final List<String> FALLBACK_PATHS = List.of(
            "/", // home

            // FIX #2: DE - najpierw impressum
            "/impressum", "/impressum/",
            "/kontakt", "/kontakt/",
            "/contact", "/contact/",

            // dodatkowe (często istnieją)
            "/kontaktformular", "/kontakt-formular",

            // na końcu prywatność
            "/datenschutz", "/datenschutz/"
    );


    public Set<FarmLead> scrapeFarmLeads(String startUrl) {

        // FIX #1: canonical start url (redirect + www/no-www)
        String resolvedStartUrl = resolveWorkingStartUrl(startUrl);
        if (!resolvedStartUrl.equals(startUrl)) {
            log.info("FarmScraperService: resolved startUrl {} -> {}", startUrl, resolvedStartUrl);
        }

        // domain licz z canonical url (nie z oryginału)
        String domain = extractBaseDomainFromUrl(resolvedStartUrl);
        if (domain == null) {
            log.warn("FarmScraperService: cannot extract base domain from {}, aborting scrape", resolvedStartUrl);
            return Set.of();
        }

        // skip scraping po canonical domain
        if (shouldSkipScraping(domain)) {
            log.info("FarmScraperService: skipping scraping for domain={} (recently scraped)", domain);
            return Set.of();
        }

        // 1) crawl
        Set<String> urlsToScrape = domainCrawler.crawlContacts(resolvedStartUrl, 1);

        // fallback jeśli crawler nic nie zwrócił
        if (urlsToScrape == null || urlsToScrape.isEmpty()) {
            urlsToScrape = buildFallbackUrls(resolvedStartUrl);
            log.info("FarmScraperService: crawler returned 0 urls, using fallback urls for startUrl={} -> {}",
                    resolvedStartUrl, urlsToScrape.size());
        }

        log.info("Urls to scrape for {}: {}", resolvedStartUrl, urlsToScrape);

        Set<String> knownEmails = repository.findAll().stream()
                .map(FarmLead::getEmail)
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        Set<FarmLead> newFarmLeads = new LinkedHashSet<>();

        // NEW: warunek "czy był realny sukces HTTP" (żeby nie ustawiać lastScrapedAt po pustym runie)
        boolean anyPageFetchedOk = false;

        for (String url : urlsToScrape) {

            Document doc;
            try {
                doc = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .referrer(REFERRER)
                        .timeout(10_000)
                        .get();

                anyPageFetchedOk = true; // <<< NEW

            } catch (Exception e) {
                log.warn("Failed to fetch {}, skipping. Reason: {}", url, e.toString());
                continue;
            }

            String html = doc.html();

            Set<String> pageEmails = emailExtractor.extractEmails(html);
            log.info("Found {} raw emails on {}", pageEmails.size(), url);

            if (pageEmails.isEmpty()) {
                continue;
            }

            for (String pageEmail : pageEmails) {
                if (pageEmail == null) continue;

                pageEmail = pageEmail.trim();
                if (pageEmail.isBlank()) continue;

                if (!isRelevantEmailForDomain(pageEmail, resolvedStartUrl)) {
                    log.info("Skipping non-relevant email '{}' for startUrl '{}'", pageEmail, resolvedStartUrl);
                    continue;
                }

                String lower = pageEmail.toLowerCase(Locale.ROOT);
                if (knownEmails.contains(lower)) {
                    log.info("Email '{}' already exists, skipping", pageEmail);
                    continue;
                }

                log.info("New farm lead on {} -> {}", url, lower);

                FarmLead farmLead = FarmLead.builder()
                        .email(lower)
                        .sourceUrl(url)
                        .createdAt(LocalDateTime.now())
                        .active(true)
                        .unsubscribeToken(TokenGenerator.generateShortToken())
                        .build();

                repository.save(farmLead);

                knownEmails.add(lower);
                newFarmLeads.add(farmLead);
            }
        }

        // NEW: lastScrapedAt tylko jeśli miało sens (fetch OK lub znaleziono leady)
        boolean successRun = anyPageFetchedOk || !newFarmLeads.isEmpty();
        if (successRun) {
            updateLastScrapedAt(domain);
        } else {
            log.info("FarmScraperService: NOT updating lastScrapedAt for domain={} because nothing was fetched and no leads were found",
                    domain);
        }

        return newFarmLeads;
    }

    private Set<String> buildFallbackUrls(String startUrl) {
        String root = rootUrl(startUrl);
        if (root == null) return Set.of();

        int maxFallbackUrls = 6; // FIX #2: limit

        Set<String> urls = new LinkedHashSet<>();
        for (String path : FALLBACK_PATHS) {
            urls.add(join(root, path));
            if (urls.size() >= maxFallbackUrls) break;
        }
        return urls;
    }


    private String rootUrl(String url) {
        try {
            URI uri = new URI(url);
            String scheme = (uri.getScheme() == null) ? "https" : uri.getScheme();
            String host = uri.getHost();
            if (host == null) return null;
            return scheme + "://" + host;
        } catch (Exception e) {
            return null;
        }
    }

    private String join(String root, String path) {
        if (root.endsWith("/") && path.startsWith("/")) {
            return root.substring(0, root.length() - 1) + path;
        }
        if (!root.endsWith("/") && !path.startsWith("/")) {
            return root + "/" + path;
        }
        return root + path;
    }

    private boolean isRelevantEmailForDomain(String email, String startUrl) {
        String emailDomain = extractDomainFromEmail(email);
        if (emailDomain == null) return false;

        String localPart = email.substring(0, email.indexOf('@'));
        if (looksLikeHexId(localPart)) return false;

        String siteBaseDomain = extractBaseDomainFromUrl(startUrl);
        if (siteBaseDomain == null) return false;

        if (emailDomain.equalsIgnoreCase(siteBaseDomain) ||
                emailDomain.endsWith("." + siteBaseDomain)) {
            return true;
        }

        Set<String> allowedPersonalDomains = Set.of(
                "gmail.com",
                "gmx.de",
                "web.de",
                "t-online.de",
                "outlook.com",
                "hotmail.com",
                "yahoo.de",
                "yahoo.com",
                "aol.com",
                "mail.de",
                "freenet.de",
                "posteo.de"
        );
        if (allowedPersonalDomains.contains(emailDomain.toLowerCase())) {
            return true;
        }

        Set<String> blacklistedDomains = Set.of(
                "mysite.com",
                "example.com",
                "test.com",
                "localhost",

                "wixpress.com",
                "sentry.wixpress.com",
                "sentry-next.wixpress.com",
                "sentry.io",

                "mailchimp.com",
                "sendgrid.net",
                "sparkpostmail.com",
                "amazonses.com"
        );

        if (blacklistedDomains.contains(emailDomain.toLowerCase())) {
            return false;
        }

        return false;
    }

    private boolean looksLikeHexId(String localPart) {
        if (localPart == null || localPart.length() < 16) return false;
        return localPart.matches("[0-9a-fA-F]+");
    }

    private String extractDomainFromEmail(String email) {
        int at = email.indexOf('@');
        if (at < 0 || at == email.length() - 1) return null;
        return email.substring(at + 1).toLowerCase();
    }

    private String extractBaseDomainFromUrl(String url) {
        try {
            String host = new URI(url).getHost();
            if (host == null) return null;
            host = host.toLowerCase();
            String[] parts = host.split("\\.");
            if (parts.length < 2) return host;
            String last = parts[parts.length - 1];
            String secondLast = parts[parts.length - 2];
            return secondLast + "." + last;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean shouldSkipScraping(String domain) {
        long minHoursBetweenScrapes = leadFinderProperties.getScraper().getMinHoursBetweenScrapes();

        return farmSourceRepository.findByDomain(domain)
                .map(source -> {
                    LocalDateTime last = source.getLastScrapedAt();
                    if (last == null) return false;

                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime threshold = now.minusHours(minHoursBetweenScrapes);

                    boolean skip = last.isAfter(threshold);
                    if (skip) {
                        long hoursSince = Duration.between(last, now).toHours();
                        log.info(
                                "FarmScraperService: domain={} lastScrapedAt={}, {}h ago (< {}h), skipping",
                                domain, last, hoursSince, minHoursBetweenScrapes
                        );
                    }
                    return skip;
                })
                .orElse(false);
    }

    private void updateLastScrapedAt(String domain) {
        FarmSource source = farmSourceRepository.findByDomain(domain)
                .orElseGet(FarmSource::new);

        boolean isNew = (source.getId() == null);

        source.setDomain(domain);
        source.setLastScrapedAt(LocalDateTime.now());

        farmSourceRepository.save(source);

        if (isNew) {
            log.info("FarmScraperService: created FarmSource domain={} with lastScrapedAt=now", domain);
        } else {
            log.info("FarmScraperService: updated FarmSource domain={} lastScrapedAt=now", domain);
        }
    }

    private String resolveWorkingStartUrl(String startUrl) {
        UrlProbeResult r1 = probeUrl(startUrl);
        if (r1.ok()) return r1.finalUrl();

        // FIX: spróbuj dodać/zdjąć trailing slash (częsty przypadek 404)
        String slashToggled = toggleTrailingSlash(startUrl);
        if (slashToggled != null && !slashToggled.equals(startUrl)) {
            UrlProbeResult rSlash = probeUrl(slashToggled);
            if (rSlash.ok()) return rSlash.finalUrl();
        }

        // tylko przy 404/410 próbujemy togglować www
        if (r1.status() == 404 || r1.status() == 410) {
            String toggledWww = toggleWww(startUrl);
            if (toggledWww != null) {
                UrlProbeResult r2 = probeUrl(toggledWww);
                if (r2.ok()) return r2.finalUrl();

                // i jeszcze www + slash toggle
                String toggledWwwSlash = toggleTrailingSlash(toggledWww);
                if (toggledWwwSlash != null) {
                    UrlProbeResult r3 = probeUrl(toggledWwwSlash);
                    if (r3.ok()) return r3.finalUrl();
                }
            }
        }

        return startUrl;
    }

    private String toggleTrailingSlash(String url) {
        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            if (path == null || path.isBlank()) path = "/";

            if (path.equals("/")) {
                return url; // root zostaw
            }

            String newPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path + "/";

            return new URI(
                    uri.getScheme(),
                    uri.getAuthority(),
                    newPath,
                    uri.getQuery(),
                    uri.getFragment()
            ).toString();

        } catch (Exception e) {
            return null;
        }
    }


    private UrlProbeResult probeUrl(String url) {
        try {
            var res = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .referrer(REFERRER)
                    .timeout(10_000)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .execute();

            int status = res.statusCode();
            String finalUrl = (res.url() != null) ? res.url().toString() : url;

            // FIX: 2xx i 3xx traktujemy jako OK
            boolean ok = status >= 200 && status < 400;

            return new UrlProbeResult(ok, status, finalUrl);

        } catch (Exception e) {
            return new UrlProbeResult(false, -1, url);
        }
    }


    private String toggleWww(String url) {
        try {
            URI uri = new URI(url);
            String scheme = (uri.getScheme() == null) ? "https" : uri.getScheme();
            String host = uri.getHost();
            if (host == null) return null;

            String newHost = host.startsWith("www.") ? host.substring(4) : "www." + host;

            return new URI(scheme, newHost, uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
        } catch (Exception e) {
            return null;
        }
    }

    private record UrlProbeResult(boolean ok, int status, String finalUrl) {}

}
