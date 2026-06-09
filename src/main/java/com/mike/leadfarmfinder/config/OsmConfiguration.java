package com.mike.leadfarmfinder.config;

import com.mike.leadfarmfinder.service.osm.OsmFarmSource;
import com.mike.leadfarmfinder.service.osm.OsmProperties;
import com.mike.leadfarmfinder.service.osm.OverpassApiClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OsmConfiguration {

    /**
     * OsmFarmSource NIE jest @Component — nie wchodzi do List<DirectorySource>
     * wstrzykiwanej do DirectoryCrawlerService.
     *
     * Dzięki temu DirectoryCronJob (codziennie) nie odpytuje Overpass API.
     * Tylko OsmCronJob (raz na tydzień) używa tego beana bezpośrednio.
     */
    @Bean
    public OsmFarmSource osmFarmSource(OverpassApiClient overpassApiClient,
                                       OsmProperties osmProperties) {
        return new OsmFarmSource(overpassApiClient, osmProperties);
    }
}