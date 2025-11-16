package com.mike.leadfarmfinder.controller;

import com.mike.leadfarmfinder.repository.FarmLeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/leads")
@RequiredArgsConstructor
public class LeadController {
    private final FarmLeadRepository farmLeadRepository;

    @GetMapping("/unsubscribe")
    public ResponseEntity<String> unsubscribe(@RequestParam String token) {
        return farmLeadRepository.findByUnsubscribeToken(token)
                .map(lead -> {
                    lead.setActive(false);
                    farmLeadRepository.save(lead);
                    return ResponseEntity.ok("Successfully unsubscribed");
                })
                .orElse(ResponseEntity.badRequest().body("Could not unsubscribe from lead"));
    }


}
