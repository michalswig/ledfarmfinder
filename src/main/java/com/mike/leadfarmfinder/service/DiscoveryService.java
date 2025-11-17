package com.mike.leadfarmfinder.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DiscoveryService {

    private final SerpApiService serpApiService;

    public List<String> findCandidateFarmUrls(int limit) {

        String query = "Saisonarbeit Erdbeeren Hof Niedersachsen";

        List<String> urlLeads = serpApiService.searchUrls(query, limit * 2);

        if (urlLeads.size() >= limit) {
            return urlLeads.subList(0, limit);
        }
        // TODO: proste filtrowanie np. żeby nie zwracać duplikatów / pustych
        return urlLeads.stream()
                .filter(Objects::nonNull)
                .distinct()
                .limit(limit)
                .toList();
    }

}
