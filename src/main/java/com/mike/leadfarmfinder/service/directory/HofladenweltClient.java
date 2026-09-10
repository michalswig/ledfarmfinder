package com.mike.leadfarmfinder.service.directory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class HofladenweltClient implements DirectorySource {

    private static final String BASE_URL = "https://hofladenwelt.com";
    private static final int TIMEOUT_MS = 15_000;
    private static final int CRAWL_DELAY_MS = 300;

    private static final List<String> BUNDESLAND_PATHS = List.of(
            "/baden-wuerttemberg/", "/bayern/", "/berlin/", "/brandenburg/",
            "/bremen/", "/hamburg/", "/hessen/", "/mecklenburg-vorpommern/",
            "/niedersachsen/", "/nordrhein-westfalen/", "/rheinland-pfalz/",
            "/saarland/", "/sachsen/", "/sachsen-anhalt/",
            "/schleswig-holstein/", "/thueringen/"
    );

    @Override
    public String sourceName() {
        return "hofladenwelt.com";
    }

    @Override
    public List<String> fetchFarmUrls() {
        Set<String> stadtPaths = collectStadtPaths();
        log.info("HofladenweltClient: collected {} Stadt paths", stadtPaths.size());

        Set<String> profilePaths = collectProfilePaths(stadtPaths);
        log.info("HofladenweltClient: collected {} profile paths", profilePaths.size());

        List<String> result = extractFarmUrls(profilePaths);
        log.info("HofladenweltClient: finished, totalUrls={}", result.size());
        return result;
    }

    private Set<String> collectStadtPaths() {
        Set<String> paths = new HashSet<>();
        for (String bundeslandPath : BUNDESLAND_PATHS) {
            String bundeslandUrl = BASE_URL + bundeslandPath;
            try {
                Document doc = fetch(bundeslandUrl);
                doc.select("a[href^=" + bundeslandUrl + "]")
                        .stream()
                        .map(el -> el.attr("href"))
                        .filter(href -> href.matches(bundeslandUrl + "[^/]+/"))
                        .forEach(paths::add);
                log.debug("HofladenweltClient: bundesland={} total städte so far={}", bundeslandPath, paths.size());
                sleep();
            } catch (Exception e) {
                log.warn("HofladenweltClient: failed bundesland={} — {}", bundeslandPath, e.getMessage());
            }
        }
        return paths;
    }

    private Set<String> collectProfilePaths(Set<String> stadtPaths) {
        Set<String> paths = new HashSet<>();
        for (String stadtUrl : stadtPaths) {
            try {
                Document doc = fetch(stadtUrl);
                doc.select("a[href^=" + stadtUrl + "]")
                        .stream()
                        .map(el -> el.attr("href"))
                        .filter(href -> !href.equals(stadtUrl) && href.endsWith("/"))
                        .forEach(paths::add);
                sleep();
            } catch (Exception e) {
                log.warn("HofladenweltClient: failed stadt={} — {}", stadtUrl, e.getMessage());
            }
        }
        return paths;
    }

    private List<String> extractFarmUrls(Set<String> profilePaths) {
        List<String> result = new ArrayList<>();
        for (String profileUrl : profilePaths) {
            try {
                Document doc = fetch(profileUrl);
                String farmUrl = doc.select("a[href^=http]:not([href*=hofladenwelt.com])")
                        .stream()
                        .map(el -> el.attr("href"))
                        .filter(href -> !href.contains("google.com")
                                && !href.contains("facebook.com")
                                && !href.contains("instagram.com")
                                && !href.contains("maps.apple.com")
                                && !href.contains("twitter.com")
                                && !href.contains("youtube.com"))
                        .findFirst()
                        .orElse(null);

                if (farmUrl != null) {
                    result.add(farmUrl);
                    log.debug("HofladenweltClient: profile={} farmUrl={}", profileUrl, farmUrl);
                } else {
                    log.debug("HofladenweltClient: profile={} no external url found", profileUrl);
                }
                sleep();
            } catch (Exception e) {
                log.warn("HofladenweltClient: failed profile={} — {}", profileUrl, e.getMessage());
            }
        }
        return result;
    }

    private Document fetch(String url) throws Exception {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(TIMEOUT_MS)
                .get();
    }

    private void sleep() {
        try {
            Thread.sleep(CRAWL_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}