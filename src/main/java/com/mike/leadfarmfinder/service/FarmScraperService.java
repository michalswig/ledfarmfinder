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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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

    /**
     * High-level API: scrape the start URL and its Kontakt/Impressum/Contact pages.
     */
    public Set<FarmLead> scrapeFarmLeads(String startUrl) {

        // 0. Wyciągamy "base domain" z URL-a – tej domeny pilnujemy w farm_sources
        String domain = extractBaseDomainFromUrl(startUrl);
        if (domain == null) {
            log.warn("FarmScraperService: cannot extract base domain from {}, aborting scrape", startUrl);
            return Set.of();
        }

        // 0.1. Sprawdź, czy ta domena nie była scrapowana niedawno
        if (shouldSkipScraping(domain)) {
            log.info("FarmScraperService: skipping scraping for domain={} (recently scraped)", domain);
            return Set.of();
        }

        // 1. Find all relevant URLs on this domain (start + kontakt/impressum/contact)
        Set<String> urlsToScrape = domainCrawler.crawlContacts(startUrl, 1); // depth=1 usually enough

        log.info("Urls to scrape for {}: {}", startUrl, urlsToScrape);

        // 2. Load known emails once to avoid duplicates
        Set<String> knownEmails = repository.findAll().stream()
                .map(FarmLead::getEmail)
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        Set<FarmLead> newFarmLeads = new LinkedHashSet<>();

        // 3. Scrape each URL
        for (String url : urlsToScrape) {

            Document doc;
            try {
                doc = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .referrer(REFERRER)
                        .timeout(10_000)
                        .get();
            } catch (Exception e) {
                log.warn("Failed to fetch {}, skipping. Reason: {}", url, e.toString());
                continue; // nie wywalaj całej apki, tylko pomiń ten URL
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

                // odfiltruj śmieciowe / niepowiązane maile
                if (!isRelevantEmailForDomain(pageEmail, startUrl)) {
                    log.info("Skipping non-relevant email '{}' for startUrl '{}'", pageEmail, startUrl);
                    continue;
                }

                String lowerCasePageEmail = pageEmail.toLowerCase();
                if (knownEmails.contains(lowerCasePageEmail)) {
                    log.info("Email '{}' already exists, skipping", pageEmail);
                    continue;
                }

                log.info("New farm lead on {} -> {}", url, lowerCasePageEmail);

                FarmLead farmLead = FarmLead.builder()
                        .email(lowerCasePageEmail)
                        .sourceUrl(url)
                        .createdAt(LocalDateTime.now())
                        .active(true)
                        .unsubscribeToken(TokenGenerator.generateShortToken())
                        .build();

                repository.save(farmLead);

                knownEmails.add(lowerCasePageEmail);
                newFarmLeads.add(farmLead);
            }
        }

        // 4. Po zakończonym scrapowaniu zaktualizuj last_scraped_at dla domeny
        updateLastScrapedAt(domain);

        return newFarmLeads;
    }

    /**
     * Czy email wygląda na sensowny lead dla danego gospodarstwa (startUrl)?
     * - preferujemy maile z tej samej domeny (np. obsthof-xyz.de)
     * - dopuszczamy prywatne domeny (gmail.com, gmx.de, mail.de, freenet.de, posteo.de, itp.)
     * - odrzucamy oczywiste śmieci / techniczne domeny
     */
    private boolean isRelevantEmailForDomain(String email, String startUrl) {
        String emailDomain = extractDomainFromEmail(email);
        if (emailDomain == null) {
            return false;
        }

        String localPart = email.substring(0, email.indexOf('@'));
        if (looksLikeHexId(localPart)) {
            // np. techniczne ID typu "a3f4b8c9d0e1f2a3" – to zwykle nie jest lead
            return false;
        }

        String siteBaseDomain = extractBaseDomainFromUrl(startUrl);
        if (siteBaseDomain == null) {
            return false;
        }

        // 1) Ten sam „base domain”: np. obsthof-wicke.de
        if (emailDomain.equalsIgnoreCase(siteBaseDomain) ||
                emailDomain.endsWith("." + siteBaseDomain)) {
            return true;
        }

        // 2) Popularne prywatne domeny – bardzo częste na małych rodzinnych gospodarstwach
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
                // typowe niemieckie prywatne
                "mail.de",
                "freenet.de",
                "posteo.de"
        );
        if (allowedPersonalDomains.contains(emailDomain.toLowerCase())) {
            return true;
        }

        // 3) Blacklista typowych śmieci / technicznych domen
        Set<String> blacklistedDomains = Set.of(
                // typowe templaty / testy
                "mysite.com",
                "example.com",
                "test.com",
                "localhost",

                // wix / sentry techniczne
                "wixpress.com",
                "sentry.wixpress.com",
                "sentry-next.wixpress.com",
                "sentry.io",

                // mailing / automaty – jeśli nie chcesz ich traktować jako leady
                "mailchimp.com",
                "sendgrid.net",
                "sparkpostmail.com",
                "amazonses.com"
        );

        if (blacklistedDomains.contains(emailDomain.toLowerCase())) {
            return false;
        }

        // 4) Domyślnie: nie bierzemy maili z innych domen (agencje, portale, vendorzy)
        return false;
    }

    private boolean looksLikeHexId(String localPart) {
        if (localPart == null || localPart.length() < 16) {
            return false;
        }
        return localPart.matches("[0-9a-fA-F]+");
    }

    private String extractDomainFromEmail(String email) {
        int at = email.indexOf('@');
        if (at < 0 || at == email.length() - 1) return null;
        return email.substring(at + 1).toLowerCase();
    }

    /**
     * Bardzo prosta ekstrakcja base-domain z URL:
     * np. https://www.obsthof-wicke.de/contact -> obsthof-wicke.de
     */
    private String extractBaseDomainFromUrl(String url) {
        try {
            String host = new java.net.URI(url).getHost();
            if (host == null) return null;
            host = host.toLowerCase();
            String[] parts = host.split("\\.");
            if (parts.length < 2) return host;
            String last = parts[parts.length - 1];
            String secondLast = parts[parts.length - 2];
            return secondLast + "." + last; // np. obsthof-wicke.de
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Sprawdza, czy scrapowanie tej domeny powinno być pominięte,
     * bo było robione zbyt niedawno.
     * Używa configu: leadfinder.scraper.min-hours-between-scrapes.
     */
    private boolean shouldSkipScraping(String domain) {
        long minHoursBetweenScrapes = leadFinderProperties.getScraper().getMinHoursBetweenScrapes();

        return farmSourceRepository.findByDomain(domain)
                .map(source -> {
                    LocalDateTime last = source.getLastScrapedAt();
                    if (last == null) {
                        return false;
                    }
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

    /**
     * Upsert last_scraped_at w farm_sources.
     */
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
