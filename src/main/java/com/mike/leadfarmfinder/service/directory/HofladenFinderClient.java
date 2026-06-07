package com.mike.leadfarmfinder.service.directory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class HofladenFinderClient implements DirectorySource {

    private static final int PAGE_SIZE = 10;

    private final RestTemplate restTemplate;
    private final HofladenFinderProperties properties;

    @Override
    public String sourceName() {
        return "hofladenfinder.org";
    }

    @Override
    public List<String> fetchFarmUrls() {
        List<String> allUrls = new ArrayList<>();

        for (String product : properties.products()) {
            List<String> productUrls = fetchForProduct(product);
            log.info("HofladenFinderClient: product={} urls={}", product, productUrls.size());
            allUrls.addAll(productUrls);
        }

        log.info("HofladenFinderClient: finished, totalUrls={}", allUrls.size());
        return allUrls;
    }

    private List<String> fetchForProduct(String product) {
        Set<String> seen = new HashSet<>();
        List<String> result = new ArrayList<>();

        int page = 1;
        int totalCount = Integer.MAX_VALUE;

        while (page <= properties.maxPagesPerProduct()) {

            int fetchedSoFar = (page - 1) * PAGE_SIZE;
            if (fetchedSoFar >= totalCount) {
                log.debug("HofladenFinderClient: product={} all pages fetched, stopping", product);
                break;
            }

            HofladenFinderResponse response = fetchPage(product, page);
            if (response == null) {
                log.warn("HofladenFinderClient: product={} page={} null response, stopping", product, page);
                break;
            }

            if (page == 1) {
                totalCount = response.totalCount();
                log.debug("HofladenFinderClient: product={} totalCount={}", product, totalCount);
            }

            List<HofladenFinderLocation> locations = response.locations();
            if (locations.isEmpty()) {
                log.debug("HofladenFinderClient: product={} page={} empty, stopping", product, page);
                break;
            }

            for (HofladenFinderLocation loc : locations) {
                String normalized = normalizeWebsite(loc.website());
                if (normalized == null) {
                    log.debug("HofladenFinderClient: skipping id={} '{}' — no website", loc.id(), loc.name());
                    continue;
                }
                if (seen.add(normalized)) {
                    result.add(normalized);
                } else {
                    log.debug("HofladenFinderClient: duplicate url={}, skipping", normalized);
                }
            }

            page++;
        }

        return result;
    }

    private HofladenFinderResponse fetchPage(String product, int pageNumber) {
        URI uri = UriComponentsBuilder.fromHttpUrl(properties.baseUrl())
                .queryParam("Product", product)
                .queryParam("ZipCodeOrPlace", "")
                .queryParam("PageNumber", pageNumber)
                .encode()
                .build()
                .toUri();

        try {
            return restTemplate.getForObject(uri, HofladenFinderResponse.class);
        } catch (RestClientException e) {
            log.warn("HofladenFinderClient: HTTP error product={} page={} — {}", product, pageNumber, e.getMessage());
            return null;
        }
    }

    static String normalizeWebsite(String website) {
        if (website == null || website.isBlank()) {
            return null;
        }
        String trimmed = website.strip();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return "https://" + trimmed;
    }
}