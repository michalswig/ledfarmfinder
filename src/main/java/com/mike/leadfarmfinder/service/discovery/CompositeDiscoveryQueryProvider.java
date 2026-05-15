// src/main/java/com/mike/leadfarmfinder/service/discovery/CompositeDiscoveryQueryProvider.java
package com.mike.leadfarmfinder.service.discovery;

import com.mike.leadfarmfinder.config.LeadFinderProperties;
import com.mike.leadfarmfinder.entity.SerpQueryOverride;
import com.mike.leadfarmfinder.repository.SerpQueryOverrideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompositeDiscoveryQueryProvider implements DiscoveryQueryProvider {

    private final LeadFinderProperties leadFinderProperties;
    private final SerpQueryOverrideRepository overrideRepository;

    @Override
    public List<String> getQueries() {
        List<String> yamlQueries = leadFinderProperties.getDiscovery().getQueries();
        if (yamlQueries == null || yamlQueries.isEmpty()) {
            return List.of();
        }

        List<SerpQueryOverride> activeOverrides = overrideRepository.findByActiveTrue();

        if (activeOverrides.isEmpty()) {
            log.debug("CompositeDiscoveryQueryProvider: no active overrides, using {} YAML queries", yamlQueries.size());
            return yamlQueries;
        }

        // Buduj mapę: originalQuery -> overrideQuery
        Map<String, String> overrideMap = new HashMap<>();
        for (SerpQueryOverride override : activeOverrides) {
            overrideMap.put(override.getOriginalQuery(), override.getOverrideQuery());
        }

        // Podmień queries z YAMLa jeśli mają aktywny override
        List<String> result = new ArrayList<>(yamlQueries.size());
        int replacedCount = 0;
        for (String query : yamlQueries) {
            String override = overrideMap.get(query);
            if (override != null) {
                result.add(override);
                replacedCount++;
            } else {
                result.add(query);
            }
        }

        log.info("CompositeDiscoveryQueryProvider: returning {} queries ({} replaced by overrides)",
                result.size(), replacedCount);
        return result;
    }
}