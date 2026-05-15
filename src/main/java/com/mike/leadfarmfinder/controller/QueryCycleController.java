package com.mike.leadfarmfinder.controller;

import com.mike.leadfarmfinder.config.LeadFinderProperties;
import com.mike.leadfarmfinder.entity.SerpQueryScore;
import com.mike.leadfarmfinder.repository.SerpQueryScoreRepository;
import com.mike.leadfarmfinder.service.serpquery.SerpQueryCyclePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/admin/query-cycle")
@RequiredArgsConstructor
public class QueryCycleController {

    private final SerpQueryCyclePort serpQueryCyclePort;
    private final SerpQueryScoreRepository serpQueryScoreRepository;
    private final LeadFinderProperties leadFinderProperties;

    @Value("${app.admin-token:local-dev-token}")
    private String adminToken;

    @PostMapping("/run")
    public ResponseEntity<RunResponse> run(
            @RequestHeader("X-Admin-Token") String token
    ) {
        if (!adminToken.equals(token)) {
            return ResponseEntity.status(403).build();
        }

        List<String> queries = leadFinderProperties.getDiscovery().getQueries();
        if (queries == null || queries.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new RunResponse(0, 0, "No queries configured"));
        }

        log.info("QueryCycleController: manual cycle triggered for {} queries", queries.size());
        List<String> replaced = serpQueryCyclePort.runCycle(queries);

        return ResponseEntity.ok(new RunResponse(queries.size(), replaced.size(), "OK"));
    }

    @GetMapping("/scores")
    public ResponseEntity<List<ScoreResponse>> scores(
            @RequestHeader("X-Admin-Token") String token,
            @RequestParam(defaultValue = "100") int threshold
    ) {
        if (!adminToken.equals(token)) {
            return ResponseEntity.status(403).build();
        }

        List<SerpQueryScore> scores = threshold >= 100
                ? serpQueryScoreRepository.findAll()
                : serpQueryScoreRepository.findByScoreLessThan(threshold);

        List<ScoreResponse> response = scores.stream()
                .map(s -> new ScoreResponse(
                        s.getQuery(),
                        s.getScore(),
                        s.getAcceptedUrls(),
                        s.getRejectedUrls(),
                        s.getRunsCount(),
                        s.getLastEvaluatedAt() != null ? s.getLastEvaluatedAt().toString() : null
                ))
                .toList();

        return ResponseEntity.ok(response);
    }

    public record RunResponse(int totalQueries, int replacedCount, String status) {}

    public record ScoreResponse(
            String query,
            int score,
            int acceptedUrls,
            int rejectedUrls,
            int runsCount,
            String lastEvaluatedAt
    ) {}
}