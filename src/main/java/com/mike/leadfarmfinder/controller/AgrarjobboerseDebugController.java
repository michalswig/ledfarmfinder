package com.mike.leadfarmfinder.controller;

import com.mike.leadfarmfinder.service.ajb.AgrarjobboerseScraperService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/debug/ajb")
@RequiredArgsConstructor
public class AgrarjobboerseDebugController {

    private final AgrarjobboerseScraperService scraperService;

    @Value("${app.admin-token:local-dev-token}")
    private String adminToken;

    @PostMapping("/run-once")
    public ResponseEntity<String> runOnce(@RequestHeader("X-ADMIN-TOKEN") String token) {
        if (!adminToken.equals(token)) return ResponseEntity.status(403).body("Forbidden");

        try {
            scraperService.runOnce();
            return ResponseEntity.ok("AJB run triggered");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("AJB failed: " + e.getMessage());
        }
    }
}
