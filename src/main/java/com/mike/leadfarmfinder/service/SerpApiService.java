package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.entity.SerpApiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SerpApiService {

    private final SerpApiProperties props;
    private final RestClient restClient = RestClient.create();

    public List<String> searchUrls(String query, int limit) {
        // TODO: zbudować URL z parametrami
        // &engine=google&hl=de&gl=de&num=10&api_key=...
        // wysłać GET, sparsować JSON, wyciągnąć linki
        return List.of("https://www.enderhof.de/");
    }
}