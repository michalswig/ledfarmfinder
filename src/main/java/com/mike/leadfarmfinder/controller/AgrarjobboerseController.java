package com.mike.leadfarmfinder.controller;

import com.mike.leadfarmfinder.dto.AjbRunSummary;
import com.mike.leadfarmfinder.service.ajb.AgrarjobboerseScraperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/ajb")
@RequiredArgsConstructor
@Slf4j
public class AgrarjobboerseController {

    private final AgrarjobboerseScraperService scraperService;

    @Value("${leadfinder.agrarjobboerse.admin-token:}")
    private String adminToken;

    private final AtomicBoolean running = new AtomicBoolean(false);

    // prosta pamięć statusu
    private final ConcurrentMap<String, RunState> runs = new ConcurrentHashMap<>();

    // executor dla joba (1 wątek wystarczy)
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ajb-manual-runner");
        t.setDaemon(true);
        return t;
    });

    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runAsync(
            @RequestHeader(value = "X-Admin-Token", required = false) String token
    ) {
        assertAdmin(token);

        if (!running.compareAndSet(false, true)) {
            return ResponseEntity.status(409).body(Map.of(
                    "status", "ALREADY_RUNNING"
            ));
        }

        String runId = UUID.randomUUID().toString();
        runs.put(runId, RunState.running(runId));

        executor.submit(() -> {
            try {
                AjbRunSummary summary = scraperService.runOnce();
                runs.put(runId, RunState.done(runId, summary));
            } catch (Exception e) {
                log.error("AJB: manual async run failed", e);
                runs.put(runId, RunState.failed(runId, e));
            } finally {
                running.set(false);
            }
        });

        // 202 -> request przyjęty, praca trwa w tle
        return ResponseEntity.accepted().body(Map.of(
                "runId", runId,
                "status", "STARTED"
        ));
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<RunState> getRun(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable String runId
    ) {
        assertAdmin(token);
        RunState state = runs.get(runId);
        if (state == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(state);
    }

    @GetMapping("/runs/latest")
    public ResponseEntity<RunState> latest(
            @RequestHeader(value = "X-Admin-Token", required = false) String token
    ) {
        assertAdmin(token);
        return runs.values().stream()
                .max((a, b) -> a.startedAt.compareTo(b.startedAt))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("AJB controller OK");
    }

    private void assertAdmin(String token) {
        if (adminToken != null && !adminToken.isBlank()) {
            if (token == null || !adminToken.equals(token)) {
                throw new UnauthorizedException();
            }
        }
    }

    @ResponseStatus(code = org.springframework.http.HttpStatus.UNAUTHORIZED)
    private static class UnauthorizedException extends RuntimeException {}

    public record RunState(
            String runId,
            String status,          // RUNNING / DONE / FAILED
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String error,
            AjbRunSummary summary
    ) {
        static RunState running(String id) {
            return new RunState(id, "RUNNING", LocalDateTime.now(), null, null, null);
        }
        static RunState done(String id, AjbRunSummary s) {
            return new RunState(id, "DONE", LocalDateTime.now(), LocalDateTime.now(), null, s);
        }
        static RunState failed(String id, Exception e) {
            return new RunState(id, "FAILED", LocalDateTime.now(), LocalDateTime.now(), e.getMessage(), null);
        }
    }
}
