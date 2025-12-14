package com.mike.leadfarmfinder.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AjbRunSummary {
    boolean dryRun;

    int offersCollected;
    int offersVisited;
    int offersWithEmails;

    int emailsExtracted;
    int emailsUnique;
    int emailsAlreadyInDb;

    int leadsSaved;

    public static AjbRunSummary disabled() {
        return AjbRunSummary.builder()
                .dryRun(true)
                .offersCollected(0)
                .offersVisited(0)
                .offersWithEmails(0)
                .emailsExtracted(0)
                .emailsUnique(0)
                .emailsAlreadyInDb(0)
                .leadsSaved(0)
                .build();
    }

    public String toLogLine() {
        return "dryRun=" + dryRun +
                " offersCollected=" + offersCollected +
                " offersVisited=" + offersVisited +
                " offersWithEmails=" + offersWithEmails +
                " emailsExtracted=" + emailsExtracted +
                " emailsUnique=" + emailsUnique +
                " emailsAlreadyInDb=" + emailsAlreadyInDb +
                " leadsSaved=" + leadsSaved;
    }
}