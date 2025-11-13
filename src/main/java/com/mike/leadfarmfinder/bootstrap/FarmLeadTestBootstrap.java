package com.mike.leadfarmfinder.bootstrap;

import com.mike.leadfarmfinder.entity.FarmLead;
import com.mike.leadfarmfinder.repository.FarmLeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class FarmLeadTestBootstrap implements CommandLineRunner {

    private final FarmLeadRepository farmLeadRepository;

    @Override
    public void run(String... args) {
        if (farmLeadRepository.count() > 0) {
            System.out.println("FarmLeadTestBootstrap: DB already has data, skipping");
            return;
        }

        FarmLead lead = FarmLead.builder()
                .email("test@example.com")
                .sourceUrl("MANUAL_TEST")
                .createdAt(LocalDateTime.now())
                .active(true)
                .build();

        farmLeadRepository.save(lead);

        System.out.println("Saved lead id=" + lead.getId());
        System.out.println("Total leads: " + farmLeadRepository.count());
    }
}

