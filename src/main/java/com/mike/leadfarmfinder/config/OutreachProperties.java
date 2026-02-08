package com.mike.leadfarmfinder.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "leadfinder.outreach")
public class OutreachProperties {

    /**
     * Czy wysyłka outreach jest włączona.
     * Na produkcji możesz ustawić true, na dev często false.
     */
    private boolean enabled = false;

    /**
     * E-mail nadawcy, np. "jobs@twoja-firma.de"
     */
    private String fromAddress;

    /**
     * Domyślny temat pierwszego maila.
     * (LF-5.4 rozbudujemy to o templaty)
     */
    private String defaultSubject;

    /**
     * Limit maili na pojedyncze uruchomienie joba
     * (LF-5.3 wykorzystamy do batchingu).
     */
    private int maxEmailsPerRun = 20;

    /**
     * Template treści pierwszego maila.
     * Możesz używać placeholderów typu {{UNSUBSCRIBE_URL}}.
     */
    private String firstEmailBodyTemplate;

    // temat follow-up
    private String followUpSubject;

    // template follow-up
    private String followUpEmailBodyTemplate;

    // ile dni od ostatniego maila min. (domyślnie 60)
    private int followUpMinDaysSinceLastEmail = 60;

    /**
     * Jeśli true – nie wysyłamy realnych maili, tylko logujemy.
     * Na produkcji ustawisz false.
     */
    private boolean simulateOnly = true;

    private long delayBetweenEmailsMillis = 0L;

    public long getDelayBetweenEmailsMillis() {
        return delayBetweenEmailsMillis;
    }

    public void setDelayBetweenEmailsMillis(long delayBetweenEmailsMillis) {
        this.delayBetweenEmailsMillis = delayBetweenEmailsMillis;
    }

}
