package com.mike.leadfarmfinder.controller;

import com.mike.leadfarmfinder.bootstrap.OsmCronJob;
import com.mike.leadfarmfinder.service.osm.OsmProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/osm")
@RequiredArgsConstructor
@Slf4j
public class OsmAdminController {

    private final OsmCronJob osmCronJob;
    private final OsmProperties osmProperties;

    @Value("${app.admin-token}")
    private String adminToken;

    /**
     * Manualny trigger runu OSM — fire & forget.
     * Zwraca 202 natychmiast, run działa w tle.
     * Wyniki widoczne w logach Render.
     *
     * curl -X POST https://<host>/api/admin/osm/run \
     *   -H "X-Admin-Token: <token>"
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> triggerRun(
            @RequestHeader("X-Admin-Token") String token
    ) {
        if (!adminToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "invalid token"));
        }

        log.info("OsmAdminController: manual run triggered");

        Thread.ofVirtual().name("osm-admin-run").start(() -> {
            try {
                osmCronJob.run();
            } catch (Exception e) {
                log.error("OsmAdminController: run failed: {}", e.getMessage(), e);
            }
        });

        return ResponseEntity.accepted().body(Map.of(
                "status", "started",
                "message", "OSM run started in background — check Render logs"
        ));
    }

    /**
     * Status konfiguracji OSM.
     *
     * curl https://<host>/api/admin/osm/status \
     *   -H "X-Admin-Token: <token>"
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(
            @RequestHeader("X-Admin-Token") String token
    ) {
        if (!adminToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "invalid token"));
        }

        return ResponseEntity.ok(Map.of(
                "enabled", osmProperties.isEnabled(),
                "bbox", osmProperties.getBbox(),
                "overpassUrl", osmProperties.getOverpassUrl(),
                "maxUrlsPerRun", osmProperties.getMaxUrlsPerRun(),
                "cron", osmProperties.getCron()
        ));
    }
}