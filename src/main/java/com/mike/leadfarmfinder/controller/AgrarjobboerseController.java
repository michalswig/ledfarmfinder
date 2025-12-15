package com.mike.leadfarmfinder.controller;

import com.mike.leadfarmfinder.dto.AjbRunSummary;
import com.mike.leadfarmfinder.service.ajb.AgrarjobboerseScraperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/ajb")
@RequiredArgsConstructor
@Slf4j
public class AgrarjobboerseController {

    private final AgrarjobboerseScraperService scraperService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Ustaw na Render jako ENV:
     * AJB_ADMIN_TOKEN=jakisdlugitoken
     *
     * a potem wołasz:
     * curl -H "X-Admin-Token: jakisdlugitoken" https://twoj-app.onrender.com/api/ajb/run
     */
    @Value("${leadfinder.agrarjobboerse.admin-token:}")
    private String adminToken;

    @PostMapping("/run")
    public ResponseEntity<AjbRunSummary> run(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                             @RequestParam(value = "force", defaultValue = "false") boolean force) {

        if (adminToken != null && !adminToken.isBlank()) {
            if (token == null || !adminToken.equals(token)) {
                return ResponseEntity.status(401).build();
            }
        }

        if (!force && !running.compareAndSet(false, true)) {
            // 409 = konflikt, już leci run
            return ResponseEntity.status(409).body(AjbRunSummary.builder()
                    .dryRun(false)
                    .offersCollected(0)
                    .offersVisited(0)
                    .offersWithEmails(0)
                    .emailsExtracted(0)
                    .emailsUnique(0)
                    .emailsAlreadyInDb(0)
                    .leadsSaved(0)
                    .build());
        }

        try {
            AjbRunSummary summary = scraperService.runOnce();
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("AJB: manual run failed", e);
            return ResponseEntity.status(500).build();
        } finally {
            running.set(false);
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("AJB controller OK");
    }
}

