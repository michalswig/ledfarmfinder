package com.mike.leadfarmfinder.bootstrap;

import com.mike.leadfarmfinder.service.ajb.AgrarjobboerseScraperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgrarjobboerseCronJob {

    private final AgrarjobboerseScraperService scraperService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(cron = "0 35 2 * * *")
    public void run() {
        if (!running.compareAndSet(false, true)) {
            log.info("AJB: already running, skipping");
            return;
        }
        try {
            scraperService.runOnce();
        } catch (Exception e) {
            log.error("AJB: run failed", e);
        } finally {
            running.set(false);
        }
    }
}
