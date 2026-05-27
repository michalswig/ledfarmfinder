package com.mike.leadfarmfinder.controller;

import com.mike.leadfarmfinder.service.directory.DirectoryCrawlResult;
import com.mike.leadfarmfinder.service.directory.DirectoryCrawlerService;
import com.mike.leadfarmfinder.service.directory.DirectoryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/debug/directory")
@RequiredArgsConstructor
@Slf4j
public class DirectoryController {

    private final DirectoryCrawlerService crawlerService;
    private final DirectoryProperties directoryProperties;

    @Value("${app.admin-token:local-dev-token}")
    private String adminToken;

    @PostMapping("/run-once")
    public ResponseEntity<Map<String, Object>> runOnce(
            @RequestHeader("X-ADMIN-TOKEN") String token
    ) {
        if (!adminToken.equals(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        log.info("DirectoryController: manual run triggered, maxUrlsPerRun={}",
                directoryProperties.maxUrlsPerRun());

        List<DirectoryCrawlResult> results = crawlerService.crawlAll(directoryProperties.maxUrlsPerRun());

        return ResponseEntity.ok(Map.of(
                "status", "DONE",
                "processed", results.stream().mapToInt(DirectoryCrawlResult::urlsProcessed).sum(),
                "ok", results.stream().mapToInt(DirectoryCrawlResult::urlsScrapedOk).sum(),
                "errors", results.stream().mapToInt(DirectoryCrawlResult::urlsScrapedError).sum(),
                "skippedDuplicate", results.stream().mapToInt(DirectoryCrawlResult::urlsSkippedDuplicate).sum()
        ));
    }
}