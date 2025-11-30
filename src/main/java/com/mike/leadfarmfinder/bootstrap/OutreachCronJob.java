package com.mike.leadfarmfinder.bootstrap;

import com.mike.leadfarmfinder.entity.FarmLead;
import com.mike.leadfarmfinder.repository.FarmLeadRepository;
import com.mike.leadfarmfinder.service.OutreachService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutreachCronJob {

    private final FarmLeadRepository farmLeadRepository;
    private final OutreachService outreachService;

    /**
     * Co 15 minut spróbuj wysłać (na razie: zalogować) maila
     * do jakiegokolwiek leada z bazy – PÓKI CO tylko do jednego.
     *
     * LF-5.2/5.3 rozbudujemy to o:
     * - wybór leadów bez firstEmailSentAt
     * - batching po maxEmailsPerRun
     */
    @Scheduled(fixedRate = 900_000)
    public void runOutreachDryRun() {
        log.info("OutreachCronJob: started dry-run");

        Optional<FarmLead> anyLead = farmLeadRepository.findAll()
                .stream()
                .findFirst();

        anyLead.ifPresentOrElse(
                outreachService::sendFirstEmail,
                () -> log.info("OutreachCronJob: no leads in DB yet")
        );
    }
}
