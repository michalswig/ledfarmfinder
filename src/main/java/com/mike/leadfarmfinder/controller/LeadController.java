package com.mike.leadfarmfinder.controller;

import com.mike.leadfarmfinder.repository.FarmLeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LeadController {
    private final FarmLeadRepository farmLeadRepository;

    @GetMapping("/unsubscribe/{token}")
    public ResponseEntity<String> unsubscribe(@PathVariable String token) {
        return farmLeadRepository.findByUnsubscribeToken(token)
                .map(lead -> {
                    lead.setActive(false);
                    farmLeadRepository.save(lead);
                    return ResponseEntity.ok("Sie haben sich erfolgreich abgemeldet.");
                })
                .orElse(ResponseEntity.badRequest()
                        .body("Der Abmeldelink ist leider ungültig oder abgelaufen."));
    }

}
