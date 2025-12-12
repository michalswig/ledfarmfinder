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
            "/",                // home
            "/kontakt", "/kontakt/",
            "/contact", "/contact/",
            "/impressum", "/impressum/",
            "/datenschutz", "/datenschutz/"
    );

    public Set<FarmLead> scrapeFarmLeads(String startUrl) {

        String domain = extractBaseDomainFromUrl(startUrl);
        if (domain == null) {
            log.warn("FarmScraperService: cannot extract base domain from {}, aborting scrape", startUrl);
            return Set.of();
        }

        if (shouldSkipScraping(domain)) {
            log.info("FarmScraperService: skipping scraping for domain={} (recently scraped)", domain);
            return Set.of();
        }

        // 1) crawl
        Set<String> urlsToScrape = domainCrawler.crawlContacts(startUrl, 1);

        // NEW: jeśli crawler nie znalazł nic (np. startUrl=404), robimy fallback
        if (urlsToScrape == null || urlsToScrape.isEmpty()) {
            urlsToScrape = buildFallbackUrls(startUrl);
            log.info("FarmScraperService: crawler returned 0 urls, using fallback urls for startUrl={} -> {}",
                    startUrl, urlsToScrape.size());
        }

        log.info("Urls to scrape for {}: {}", startUrl, urlsToScrape);

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

                if (!isRelevantEmailForDomain(pageEmail, startUrl)) {
                    log.info("Skipping non-relevant email '{}' for startUrl '{}'", pageEmail, startUrl);
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

        Set<String> urls = new LinkedHashSet<>();
        for (String path : FALLBACK_PATHS) {
            urls.add(join(root, path));
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
}
