package com.mike.leadfarmfinder.bootstrap;

import com.mike.leadfarmfinder.config.LeadFinderProperties;
import com.mike.leadfarmfinder.service.serpquery.SerpQueryCyclePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class SerpQueryCycleCronJob {

    private final SerpQueryCyclePort serpQueryCyclePort;
    private final LeadFinderProperties leadFinderProperties;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(cron = "${leadfinder.query-cycle.cron:0 0 3 * * SUN}")
    public void run() {
        if (!running.compareAndSet(false, true)) {
            log.warn("SerpQueryCycleCronJob: previous run still in progress, skipping");
            return;
        }

        try {
            List<String> queries = leadFinderProperties.getDiscovery().getQueries();
            if (queries == null || queries.isEmpty()) {
                log.warn("SerpQueryCycleCronJob: no queries configured, skipping");
                return;
            }

            log.info("SerpQueryCycleCronJob: starting cycle for {} queries", queries.size());
            List<String> replaced = serpQueryCyclePort.runCycle(queries);
            log.info("SerpQueryCycleCronJob: cycle finished, replaced {} queries", replaced.size());

        } catch (Exception e) {
            log.error("SerpQueryCycleCronJob: unexpected error during cycle", e);
        } finally {
            running.set(false);
        }
    }
}