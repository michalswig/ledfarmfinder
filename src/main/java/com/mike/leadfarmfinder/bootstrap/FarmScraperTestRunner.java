package com.mike.leadfarmfinder.bootstrap;

import com.mike.leadfarmfinder.service.FarmScraperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
//@Component
@RequiredArgsConstructor
public class FarmScraperTestRunner implements CommandLineRunner {
    private final FarmScraperService service;

    @Override
    public void run(String... args) throws Exception {
        List<String> urls = List.of(
                "https://www.enderhof.de/"

        );

        for (String url : urls) {
            log.info("Scraping URL: {}", url);
            try {
                service.scrapeFarmLeads(url);
            } catch (Exception e) { // na wszelki wypadek
                log.warn("Scraping failed for {}: {}", url, e.toString());
            }
        }

    }
}
