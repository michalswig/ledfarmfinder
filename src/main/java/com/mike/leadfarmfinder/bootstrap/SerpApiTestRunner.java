package com.mike.leadfarmfinder.bootstrap;

import com.mike.leadfarmfinder.service.SerpApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;

import java.util.List;

@Slf4j
//@Component
@Profile("dev")
@RequiredArgsConstructor
public class SerpApiTestRunner implements CommandLineRunner {

    private final SerpApiService serpApiService;

    @Override
    public void run(String... args) {
        String query = "Saisonarbeit Erdbeeren Hof Niedersachsen";
        List<String> urls = serpApiService.searchUrls(query, 10);

        log.info("SerpApiTestRunner: got {} urls from SerpAPI", urls.size());
        urls.forEach(u -> log.info("  - {}", u));
    }
}
