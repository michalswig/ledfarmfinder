package com.mike.leadfarmfinder.controller;

import com.mike.leadfarmfinder.repository.FarmLeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LeadController {

    private final FarmLeadRepository farmLeadRepository;

    @GetMapping("/unsubscribe/{token}")
    public ResponseEntity<String> unsubscribeGet(@PathVariable String token) {
        return doUnsubscribe(token);
    }

    @PostMapping("/unsubscribe/{token}")
    public ResponseEntity<String> unsubscribePost(@PathVariable String token) {
        return doUnsubscribe(token);
    }

    private ResponseEntity<String> doUnsubscribe(String token) {
        return farmLeadRepository.findByUnsubscribeToken(token)
                .map(lead -> {
                    if (lead.isActive()) {
                        lead.setActive(false);
                        farmLeadRepository.save(lead);
                    }
                    return ResponseEntity.ok("Sie haben sich erfolgreich abgemeldet.");
                })
                .orElse(ResponseEntity.badRequest()
                        .body("Der Abmeldelink ist leider ungültig oder abgelaufen."));
    }
}
