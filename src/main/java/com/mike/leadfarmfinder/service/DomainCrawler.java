package com.mike.leadfarmfinder.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class DomainCrawler {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/129.0.0.0 Safari/537.36";

    private static final String REFERRER = "https://www.google.com";

    public Set<String> crawlContacts(String startUrl, int maxDepth) {
        Set<String> visited = new HashSet<>();
        Set<String> result = new LinkedHashSet<>();
        try {
            crawl(startUrl, maxDepth, visited, result);
        } catch (IOException e) {
            // w praktyce rzadko tu wejdziemy, bo w crawl() łapiemy soft-fail
            log.warn("Failed to crawl domain {}: {}", startUrl, e.toString());
        }
        return result;
    }

    private void crawl(String startUrl,
                       int maxDepth,
                       Set<String> visited,
                       Set<String> result) throws IOException {

        if (maxDepth < 0) return;

        // FIX C: visited po znormalizowanym URL (np. /kontakt vs /kontakt/)
        String normalizedVisitedKey = normalizeUrlForVisited(startUrl);
        if (normalizedVisitedKey == null || visited.contains(normalizedVisitedKey)) {
            return;
        }
        visited.add(normalizedVisitedKey);

        // FIX B: dodaj do result ZANIM fetchujesz (żeby 404/timeout nie dawał pustego wyniku)
        result.add(startUrl);

        Document doc;
        try {
            doc = Jsoup.connect(startUrl)
                    .userAgent(USER_AGENT)
                    .referrer(REFERRER)
                    .timeout(10_000)
                    .followRedirects(true)
                    .ignoreHttpErrors(true) // FIX B: nie wywalaj wyjątku na 404/500, często i tak jest menu/linki
                    .get();
        } catch (Exception e) {
            log.warn("DomainCrawler: failed to fetch {} (depthLeft={}) reason={}", startUrl, maxDepth, e.toString());
            return; // soft fail: wynik już ma startUrl
        }

        if (maxDepth == 0) {
            return;
        }

        doc.select("a[href]").forEach(a -> {
            String linkText = safeLower(a.text());
            String href = a.attr("href");
            String hrefLower = safeLower(href);
            String absUrl = a.absUrl("href");

            if (absUrl == null || absUrl.isBlank()) return;

            // FIX A: nie porównuj host 1:1, tylko bazową domenę + subdomeny
            if (!isSameDomain(startUrl, absUrl)) return;

            boolean looksLikeContact = looksLikeContactLink(linkText, hrefLower);

            if (looksLikeContact) {
                log.info("Contact-like link on {} -> text='{}', href='{}', abs='{}'",
                        startUrl, linkText, href, absUrl);
            } else {
                return;
            }

            try {
                crawl(absUrl, maxDepth - 1, visited, result);
            } catch (IOException e) {
                log.warn("Failed to crawl {}", absUrl, e);
            }
        });
    }

    private boolean looksLikeContactLink(String linkTextLower, String hrefLower) {
        return (linkTextLower.contains("kontakt")
                || linkTextLower.contains("impressum")
                || linkTextLower.contains("contact")
                || hrefLower.contains("kontakt")
                || hrefLower.contains("impressum")
                || hrefLower.contains("contact"));
    }

    private String safeLower(String s) {
        return (s == null) ? "" : s.toLowerCase(Locale.ROOT);
    }

    private boolean isSameDomain(String baseUrl, String otherUrl) {
        try {
            URI base = new URI(baseUrl);
            URI other = new URI(otherUrl);

            String baseHost = normalizeHost(base.getHost());
            String otherHost = normalizeHost(other.getHost());
            if (baseHost == null || otherHost == null) return false;

            String baseDomain = baseDomain(baseHost);
            String otherDomain = baseDomain(otherHost);

            if (baseDomain == null || otherDomain == null) return false;

            // to samo "secondLevel.tld"
            if (baseDomain.equalsIgnoreCase(otherDomain)) return true;

            // subdomena tej samej bazy
            return otherHost.endsWith("." + baseDomain);

        } catch (Exception e) {
            return false;
        }
    }

    private String normalizeHost(String host) {
        if (host == null) return null;
        host = host.toLowerCase(Locale.ROOT).trim();
        if (host.startsWith("www.")) host = host.substring(4);
        return host;
    }

    private String baseDomain(String host) {
        String[] parts = host.split("\\.");
        if (parts.length < 2) return host;
        return parts[parts.length - 2] + "." + parts[parts.length - 1];
    }

    // FIX C: normalizacja klucza dla visited
    private String normalizeUrlForVisited(String url) {
        if (url == null) return null;
        url = url.trim();
        if (url.endsWith("/") && url.length() > 1) return url.substring(0, url.length() - 1);
        return url;
    }
}
