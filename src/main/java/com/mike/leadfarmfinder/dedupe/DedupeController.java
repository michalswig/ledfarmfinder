package com.mike.leadfarmfinder.dedupe;

import com.mike.leadfarmfinder.entity.FarmLead;
import com.mike.leadfarmfinder.repository.FarmLeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dedupe")
@RequiredArgsConstructor
public class DedupeController {

    private final FarmLeadRepository farmLeadRepository;

    @PostMapping("/emails")
    public ResponseEntity<List<String>> getNewEmails(@RequestBody List<String> emails) {
        if (emails == null || emails.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        Set<String> knownEmails = farmLeadRepository.findAll().stream()
                .map(FarmLead::getEmail)
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        Set<String> batchEmails = new HashSet<>();

        List<String> newEmails = new ArrayList<>();

        for (String raw : emails) {
            if (raw == null) continue;

            String email = raw.trim();
            if (email.isBlank()) continue;

            String emailLower = email.toLowerCase();

            if (knownEmails.contains(emailLower)) {
                continue;
            }

            if (batchEmails.contains(emailLower)) {
                continue;
            }

            FarmLead lead = FarmLead.builder()
                    .email(email)
                    .sourceUrl("MANUAL_IMPORT")
                    .createdAt(LocalDateTime.now())
                    .active(true)
                    .unsubscribeToken(UUID.randomUUID().toString())
                    .build();

            farmLeadRepository.save(lead);

            knownEmails.add(emailLower);
            batchEmails.add(emailLower);
            newEmails.add(email);
        }

        return ResponseEntity.ok(newEmails);
    }
}
