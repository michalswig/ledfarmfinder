package com.mike.leadfarmfinder.service.discovery;

import com.mike.leadfarmfinder.config.LeadFinderProperties;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class YamlDiscoveryQueryProvider implements DiscoveryQueryProvider {

    private final LeadFinderProperties leadFinderProperties;

    @Override
    public List<String> getQueries() {
        List<String> queries = leadFinderProperties.getDiscovery().getQueries();
        return queries != null ? queries : List.of();
    }
}