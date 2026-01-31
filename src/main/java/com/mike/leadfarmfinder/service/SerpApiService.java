package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.config.LeadFinderProperties;
import com.mike.leadfarmfinder.dto.OrganicResult;
import com.mike.leadfarmfinder.dto.SerpApiSearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class SerpApiService {

    private static final int DEFAULT_RESULTS_PER_PAGE = 10;
    private static final int SERPAPI_MAX_NUM = 100;

    private final SerpApiProperties props;
    private final RestClient restClient;
    private final LeadFinderProperties leadFinderProperties;

    public SerpApiService(SerpApiProperties props, LeadFinderProperties leadFinderProperties) {
        this.props = props;
        this.leadFinderProperties = leadFinderProperties;
        this.restClient = RestClient.builder()
                .baseUrl(props.baseUrl())
                .build();
    }

    /**
     * Fetches organic result links from SerpAPI with proper pagination (start = (page-1) * num).
     *
     * @param query search query
     * @param limit max number of links the caller wants to handle (does NOT have to equal SerpAPI "num")
     * @param page  1-based page index
     */
    public List<String> searchUrls(String query, int limit, int page) {
        try {
            String apiKey = props.apiKey();
            if (apiKey == null || apiKey.isBlank()) {
                log.warn("SerpApiClient.searchUrls: missing serpapi.api-key -> returning empty (query='{}')", query);
                return Collections.emptyList();
            }

            int safePage = Math.max(page, 1);

            int configured = leadFinderProperties.getDiscovery().getResultsPerPage();
            if (configured < 1) {
                configured = DEFAULT_RESULTS_PER_PAGE;
            }

            // "num" = ile prosimy SerpAPI na jedną stronę (stabilne i przewidywalne)
            int num = Math.min(configured, SERPAPI_MAX_NUM);

            // "limit" = ile maksymalnie caller chce przetworzyć; nie wymuszamy nim num
            // (DiscoveryService może i tak uciąć listę po swoich filtrach)
            if (limit < 1) {
                limit = num;
            }

            int start = (safePage - 1) * num;

            log.info("SerpApiClient.searchUrls: querying SerpAPI. query='{}', limit={}, page={}, num={}, start={}",
                    query, limit, safePage, num, start);

            SerpApiSearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("") // base-url już zawiera /search
                            .queryParam("engine", props.defaultEngine())
                            .queryParam("hl", props.defaultLanguage())
                            .queryParam("gl", props.defaultCountry())
                            .queryParam("num", num)
                            .queryParam("start", start) // <<< KLUCZ DO PAGINACJI
                            .queryParam("q", query)
                            .queryParam("api_key", apiKey)
                            .build()
                    )
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        // Nie próbujemy czytać body "w ciemno" (bywa stream/nieczytelne); status wystarczy do diagnozy
                        log.error("SerpApiClient.searchUrls: HTTP error from SerpAPI: status={}, query='{}', page={}, start={}",
                                res.getStatusCode(), query, safePage, start);
                    })
                    .body(SerpApiSearchResponse.class);

            if (response == null || response.organicResults() == null) {
                log.warn("SerpApiClient.searchUrls: empty response or no organic_results (query='{}', page={})", query, safePage);
                return Collections.emptyList();
            }

            List<String> links = response.organicResults().stream()
                    .map(OrganicResult::link)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .distinct()
                    // honorujemy limit dopiero na końcu
                    .limit(limit)
                    .toList();

            log.info("SerpApiClient.searchUrls: got {} organic links from SerpAPI (query='{}', page={})",
                    links.size(), query, safePage);

            if (!links.isEmpty()) {
                log.debug("SerpApiClient.searchUrls: links={}", links);
            }

            return links;

        } catch (Exception e) {
            log.error("SerpApiClient.searchUrls: exception while calling SerpAPI (query='{}')", query, e);
            return Collections.emptyList();
        }
    }

    public List<String> searchUrls(String query, int limit) {
        return searchUrls(query, limit, 1);
    }
}
