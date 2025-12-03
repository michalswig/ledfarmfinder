package com.mike.leadfarmfinder.bootstrap;

import com.mike.leadfarmfinder.entity.FarmLead;
import com.mike.leadfarmfinder.repository.FarmLeadRepository;
import com.mike.leadfarmfinder.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

//@Component
@RequiredArgsConstructor
@Slf4j
public class DbBootstrap implements CommandLineRunner {

    private final FarmLeadRepository repository;

    @Override
    public void run(String... args) throws Exception {
        long existingBefore = repository.count();
        log.info("DbBootstrap: existing records in farm_leads BEFORE import = {}", existingBefore);

        if (existingBefore > 0) {
            log.info("DbBootstrap: skipping import, DB already initialized");
            return;
        }

        ClassPathResource resource = new ClassPathResource("MY_DB_EMAILS.txt");
        if (!resource.exists()) {
            log.warn("DbBootstrap: MY_DB_EMAILS.txt not found on classpath, skipping import");
            return;
        }

        int processed = 0;
        int imported = 0;

        Set<String> seenEmails = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String email = line.trim();
                if (email.isBlank()) {
                    continue;
                }
                processed++;

                String emailLower = email.toLowerCase();
                if (seenEmails.contains(emailLower)) {
                    continue;
                }

                FarmLead lead = FarmLead.builder()
                        .email(emailLower)
                        .sourceUrl("IMPORT_TXT")
                        .createdAt(LocalDateTime.now())
                        .active(true)
                        .unsubscribeToken(TokenGenerator.generateShortToken())
                        .build();

                repository.save(lead);
                seenEmails.add(emailLower);
                imported++;
            }
        }

        long existingAfter = repository.count();
        log.info("DbBootstrap: processed lines = {}", processed);
        log.info("DbBootstrap: imported {} new leads from TXT", imported);
        log.info("DbBootstrap: total leads in DB AFTER import = {}", existingAfter);
    }
}

