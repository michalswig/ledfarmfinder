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
public class DemeterClient implements DirectorySource {

    private static final String BASE_URL = "https://www.demeter.de";
    private static final String LISTING_URL = BASE_URL + "/betriebe?f[0]=business_category:1&page=";
    private static final int MAX_PAGES = 30;
    private static final int LISTING_TIMEOUT_MS = 15_000;
    private static final int DETAIL_TIMEOUT_MS = 10_000;
    private static final int CRAWL_DELAY_MS = 500;

    @Override
    public String sourceName() {
        return "demeter.de";
    }

    @Override
    public List<String> fetchFarmUrls() {
        Set<String> detailPaths = collectDetailPaths();
        log.info("DemeterClient: collected {} detail paths", detailPaths.size());

        List<String> result = extractFarmUrls(detailPaths);
        log.info("DemeterClient: finished, totalUrls={}", result.size());
        return result;
    }

    private Set<String> collectDetailPaths() {
        Set<String> paths = new HashSet<>();

        for (int page = 0; page < MAX_PAGES; page++) {
            String url = LISTING_URL + page;
            try {
                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (compatible; LeadFarmFinder/1.0)")
                        .timeout(LISTING_TIMEOUT_MS)
                        .get();

                List<String> found = doc.select("a[href^=/betriebe/]").stream()
                        .map(el -> el.attr("href"))
                        .filter(href -> !href.equals("/betriebe"))
                        .distinct()
                        .toList();

                if (found.isEmpty()) {
                    log.debug("DemeterClient: listing page={} empty, stopping", page);
                    break;
                }

                paths.addAll(found);
                log.debug("DemeterClient: listing page={} found={} paths total={}", page, found.size(), paths.size());

                sleep();

            } catch (Exception e) {
                log.warn("DemeterClient: failed listing page={} — {}", page, e.getMessage());
            }
        }

        return paths;
    }

    private List<String> extractFarmUrls(Set<String> detailPaths) {
        List<String> result = new ArrayList<>();

        for (String path : detailPaths) {
            String detailUrl = BASE_URL + path;
            try {
                Document doc = Jsoup.connect(detailUrl)
                        .userAgent("Mozilla/5.0 (compatible; LeadFarmFinder/1.0)")
                        .timeout(DETAIL_TIMEOUT_MS)
                        .get();

                String farmUrl = doc.select("a[href^=http]:not([href*=demeter.de]):not([href*=google.com])")
                        .stream()
                        .map(el -> el.attr("href"))
                        .filter(href -> !href.contains("facebook.com")
                                && !href.contains("instagram.com")
                                && !href.contains("twitter.com")
                                && !href.contains("linkedin.com")
                                && !href.contains("youtube.com")
                                && !href.contains("shop.demeter"))
                        .findFirst()
                        .orElse(null);

                if (farmUrl != null) {
                    result.add(farmUrl);
                    log.debug("DemeterClient: path={} farmUrl={}", path, farmUrl);
                } else {
                    log.debug("DemeterClient: path={} no external url found", path);
                }

                sleep();

            } catch (Exception e) {
                log.warn("DemeterClient: failed detail path={} — {}", path, e.getMessage());
            }
        }

        return result;
    }

    private void sleep() {
        try {
            Thread.sleep(CRAWL_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}