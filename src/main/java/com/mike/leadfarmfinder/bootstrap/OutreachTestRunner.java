package com.mike.leadfarmfinder.bootstrap;

import com.mike.leadfarmfinder.entity.FarmLead;
import com.mike.leadfarmfinder.repository.FarmLeadRepository;
import com.mike.leadfarmfinder.service.OutreachService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutreachTestRunner implements CommandLineRunner {

    private final FarmLeadRepository farmLeadRepository;
    private final OutreachService outreachService;

    @Override
    public void run(String... args) {
        Optional<FarmLead> anyLead = farmLeadRepository.findAll().stream()
                .filter(FarmLead::isActive)
                .findFirst();

        if (anyLead.isEmpty()) {
            log.info("OutreachTestRunner: no active leads found, skipping");
            return;
        }

        FarmLead lead = anyLead.get();
        log.info("OutreachTestRunner: sending preview email to {}", lead.getEmail());
        outreachService.sendInitialEmail(lead);
    }
}