package com.mike.leadfarmfinder.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

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
            log.warn("Failed to crawl domain {}: {}", startUrl, e.toString());
        }
        return result;
    }

    private void crawl(String startUrl,
                       int maxDepth,
                       Set<String> visited,
                       Set<String> result) throws IOException {

        if (maxDepth < 0 || visited.contains(startUrl)) {
            return;
        }
        visited.add(startUrl);

        Document doc = Jsoup.connect(startUrl)
                .userAgent(USER_AGENT)
                .referrer(REFERRER)
                .timeout(10_000)
                .get();

        result.add(startUrl);

        if (maxDepth == 0) {
            return;
        }

        doc.select("a[href]").forEach(a -> {
            String linkText = a.text().toLowerCase();
            String href = a.attr("href");
            String absUrl = a.absUrl("href");

            if (absUrl.isBlank()) return;

            // Stay on same domain
            if (!isSameDomain(startUrl, absUrl)) return;

            String hrefLower = href.toLowerCase();

            boolean looksLikeContact =
                    linkText.contains("kontakt") ||
                            linkText.contains("impressum") ||
                            linkText.contains("contact") ||
                            hrefLower.contains("kontakt") ||
                            hrefLower.contains("impressum") ||
                            hrefLower.contains("contact");

            if (looksLikeContact) {
                log.info("Contact-like link on {} -> text='{}', href='{}', abs='{}'",
                        startUrl, linkText, href, absUrl);
            }

            if (!looksLikeContact) return;

            try {
                crawl(absUrl, maxDepth - 1, visited, result);
            } catch (IOException e) {
                log.warn("Failed to crawl {}", absUrl, e);
            }
        });
    }

    private boolean isSameDomain(String baseUrl, String otherUrl) {
        try {
            URI base = new URI(baseUrl);
            URI other = new URI(otherUrl);
            return Objects.equals(base.getHost(), other.getHost());
        } catch (Exception e) {
            return false;
        }
    }
}
