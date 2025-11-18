package com.mike.leadfarmfinder.service;

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

    private final SerpApiProperties props;
    private final RestClient restClient;

    public SerpApiService(SerpApiProperties props) {
        this.props = props;
        this.restClient = RestClient.builder()
                .baseUrl(props.baseUrl())
                .build();
    }

    public List<String> searchUrls(String query, int limit, int page) {
        try {
            if (page < 1) {
                page = 1;
            }

            // ile wyników na jedną stronę z SerpAPI faktycznie prosimy
            int resultsPerPage = Math.min(limit, 10); // możesz zmienić np. na 20, jeśli chcesz

            int start = (page - 1) * resultsPerPage;

            log.info("SerpApiClient.searchUrls: querying SerpAPI. query='{}', limit={}, page={}, resultsPerPage={}, start={}",
                    query, limit, page, resultsPerPage, start);

            SerpApiSearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("") // base-url już zawiera /search
                            .queryParam("engine", props.defaultEngine())
                            .queryParam("hl", props.defaultLanguage())
                            .queryParam("gl", props.defaultCountry())
                            .queryParam("num", resultsPerPage)
                            .queryParam("start", start)          // <<< KLUCZ DO PAGINACJI
                            .queryParam("q", query)
                            .queryParam("api_key", props.apiKey())
                            .build()
                    )
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        log.error("SerpApiClient.searchUrls: HTTP error from SerpAPI: status={}, body={}",
                                res.getStatusCode(), res.getBody());
                    })
                    .body(SerpApiSearchResponse.class);

            if (response == null || response.organicResults() == null) {
                log.warn("SerpApiClient.searchUrls: empty response or no organic_results");
                return Collections.emptyList();
            }

            List<String> links = response.organicResults().stream()
                    .map(OrganicResult::link)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .toList();

            log.info("SerpApiClient.searchUrls: got {} organic links from SerpAPI", links.size());
            if (!links.isEmpty()) {
                log.debug("SerpApiClient.searchUrls: links={}", links);
            }

            return links;

        } catch (Exception e) {
            log.error("SerpApiClient.searchUrls: exception while calling SerpAPI", e);
            return Collections.emptyList();
        }
    }

    public List<String> searchUrls(String query, int limit) {
        return searchUrls(query, limit, 1);
    }
}