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

    public List<String> searchUrls(String query, int limit, int page) {
        try {
            String apiKey = props.apiKey();
            if (apiKey == null || apiKey.isBlank()) {
                log.warn("SerpApiClient.searchUrls: missing serpapi.api-key (query='{}')", query);
                return Collections.emptyList();
            }

            int safePage = Math.max(page, 1);

            int configured = leadFinderProperties.getDiscovery().getResultsPerPage();
            if (configured < 1) {
                configured = DEFAULT_RESULTS_PER_PAGE;
            }

            int num = Math.min(configured, SERPAPI_MAX_NUM);

            if (limit < 1) {
                limit = num;
            }

            int start = (safePage - 1) * num;

            log.debug("SerpApiClient.searchUrls: querying SerpAPI. query='{}', page={}, num={}, start={}",
                    query, safePage, num, start);

            SerpApiSearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("")
                            .queryParam("engine", props.defaultEngine())
                            .queryParam("hl", props.defaultLanguage())
                            .queryParam("gl", props.defaultCountry())
                            .queryParam("num", num)
                            .queryParam("start", start)
                            .queryParam("q", query)
                            .queryParam("api_key", apiKey)
                            .build()
                    )
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        log.error("SerpApiClient.searchUrls: HTTP error status={} query='{}' page={} start={}",
                                res.getStatusCode(), query, safePage, start);
                    })
                    .body(SerpApiSearchResponse.class);

            if (response == null || response.organicResults() == null) {
                log.warn("SerpApiClient.searchUrls: empty response or no organic_results (query='{}', page={})",
                        query, safePage);
                return Collections.emptyList();
            }

            List<String> links = response.organicResults().stream()
                    .map(OrganicResult::link)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .limit(limit)
                    .toList();

            log.debug("SerpApiClient.searchUrls: got {} links (query='{}', page={})",
                    links.size(), query, safePage);

            return links;

        } catch (Exception e) {
            log.error("SerpApiClient.searchUrls: exception calling SerpAPI (query='{}')", query, e);
            return Collections.emptyList();
        }
    }

    public List<String> searchUrls(String query, int limit) {
        return searchUrls(query, limit, 1);
    }
}