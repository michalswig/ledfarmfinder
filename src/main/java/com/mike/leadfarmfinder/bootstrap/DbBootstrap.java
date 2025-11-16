package com.mike.leadfarmfinder.bootstrap;

import com.mike.leadfarmfinder.entity.FarmLead;
import com.mike.leadfarmfinder.repository.FarmLeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
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

        var resource = new ClassPathResource("MY_DB_EMAILS.txt");
        if (!resource.exists()) {
            log.warn("DbBootstrap: MY_DB_EMAILS.txt not found, skipping import");
            return;
        }
        Path path = resource.getFile().toPath();

        List<String> lines = Files.readAllLines(path);
        log.info("DbBootstrap: lines in file = {}", lines.size());

        Set<String> existingEmails = new HashSet<>();
        repository.findAll().forEach(lead ->
                existingEmails.add(lead.getEmail().toLowerCase())
        );
        log.info("DbBootstrap: existing emails loaded from DB = {}", existingEmails.size());

        int processed = 0;
        int imported = 0;

        for (String raw : lines) {
            String email = raw == null ? "" : raw.trim();
            if (email.isBlank()) {
                continue;
            }
            processed++;

            String emailLower = email.toLowerCase();
            if (existingEmails.contains(emailLower)) continue;

            FarmLead lead = FarmLead.builder()
                    .email(email)
                    .sourceUrl("IMPORT_TXT")
                    .createdAt(LocalDateTime.now())
                    .active(true)
                    .build();

            repository.save(lead);

            existingEmails.add(emailLower);
            imported++;

        }

        long existingAfter = repository.count();
        log.info("DbBootstrap: processed lines = {}", processed);
        log.info("DbBootstrap: imported {} new leads from TXT", imported);
        log.info("DbBootstrap: total leads in DB AFTER import = {}", existingAfter);
    }

}
