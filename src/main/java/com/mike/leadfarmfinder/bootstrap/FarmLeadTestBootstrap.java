package com.mike.leadfarmfinder.bootstrap;

import com.mike.leadfarmfinder.entity.FarmLead;
import com.mike.leadfarmfinder.repository.FarmLeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class FarmLeadTestBootstrap implements CommandLineRunner {

    private final FarmLeadRepository farmLeadRepository;

    @Override
    public void run(String... args) throws Exception {
        if (farmLeadRepository.count() > 0) {
            log.info("FarmLead found, no import from DB");
            return;
        }
        FarmLead farmLead = FarmLead.builder()
                .email("test@example.com")
                .sourceUrl("test")
                .createdAt(LocalDateTime.now())
                .active(true)
                .build();

        farmLeadRepository.save(farmLead);

        log.info("FarmLead has been saved with id = {}", farmLead.getId());
        log.info("Total leads: {}", farmLeadRepository.count());


    }
}


