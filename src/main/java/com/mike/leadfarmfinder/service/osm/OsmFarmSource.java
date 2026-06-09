package com.mike.leadfarmfinder.service.osm;

import com.mike.leadfarmfinder.service.directory.DirectorySource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class OsmFarmSource implements DirectorySource {

    private final OverpassApiClient overpassApiClient;
    private final OsmProperties osmProperties;

    @Override
    public String sourceName() {
        return "openstreetmap.org";
    }

    @Override
    public List<String> fetchFarmUrls() {
        if (!osmProperties.isEnabled()) {
            log.info("OsmFarmSource: disabled, skipping");
            return Collections.emptyList();
        }

        log.info("OsmFarmSource: fetching farm URLs from OpenStreetMap");

        List<String> urls = overpassApiClient.fetchFarmWebsites();

        if (urls.isEmpty()) {
            log.warn("OsmFarmSource: no URLs returned from Overpass API");
            return Collections.emptyList();
        }

        // Sortowanie kluczowe — Overpass nie gwarantuje kolejności.
        // Bez stabilnej kolejności dedup przez DiscoveredUrlRepository
        // nie działa poprawnie między tygodniowymi runami.
        List<String> sorted = urls.stream()
                .sorted()
                .toList();

        log.info("OsmFarmSource: returning {} sorted farm URLs", sorted.size());
        return sorted;
    }
}