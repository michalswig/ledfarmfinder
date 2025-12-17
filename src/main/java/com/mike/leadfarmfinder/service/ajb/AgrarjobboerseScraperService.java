package com.mike.leadfarmfinder.service.ajb;

import com.mike.leadfarmfinder.config.AgrarjobboerseProperties;
import com.mike.leadfarmfinder.dto.AjbRunSummary;
import com.mike.leadfarmfinder.entity.FarmLead;
import com.mike.leadfarmfinder.repository.FarmLeadRepository;
import com.mike.leadfarmfinder.service.EmailExtractor;
import com.mike.leadfarmfinder.util.TokenGenerator;
import com.microsoft.playwright.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.microsoft.playwright.options.LoadState.NETWORKIDLE;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgrarjobboerseScraperService {

    private final AgrarjobboerseProperties props;
    private final AgrarjobboerseClient client;
    private final AjbCursorService cursorService;

    private final EmailExtractor emailExtractor;
    private final FarmLeadRepository farmLeadRepository;

    public AjbRunSummary runOnce() {
        if (!props.isEnabled()) {
            log.info("AJB: disabled");
            return AjbRunSummary.disabled();
        }

        // ✅ Cursor z DB: rezerwacja "okna stron" na ten run
        int startPage = cursorService.allocateStartPage(props.getPagesPerRun(), props.getPageCap());
        int pagesPerRun = Math.max(1, props.getPagesPerRun());

        Set<String> offerUrls = client.collectOfferUrls(startPage, pagesPerRun);
        log.info("AJB: total offer urls collected: {} (startPage={}, pagesPerRun={}, cap={})",
                offerUrls.size(), startPage, pagesPerRun, props.getPageCap());

        int offersVisited = 0;
        int offersWithEmails = 0;

        int emailsExtracted = 0;
        int emailsUnique = 0;
        int emailsAlreadyInDb = 0;
        int leadsSaved = 0;

        Set<String> uniqueRunEmails = new LinkedHashSet<>();

        // Playwright - jeden browser na cały run (stabilniej, taniej)
        try (Playwright pw = Playwright.create()) {
            Browser browser = pw.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(true)
                            .setArgs(List.of("--no-sandbox", "--disable-dev-shm-usage"))
            );

            try (BrowserContext ctx = browser.newContext()) {
                Page page = ctx.newPage();

                page.setDefaultTimeout(props.getPageTimeoutMs());
                page.setDefaultNavigationTimeout(props.getPageTimeoutMs());

                for (String offerUrl : offerUrls) {
                    offersVisited++;

                    try {
                        page.navigate(offerUrl,
                                new Page.NavigateOptions()
                                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));

                        try {
                            page.waitForLoadState(NETWORKIDLE,
                                    new Page.WaitForLoadStateOptions().setTimeout((double) props.getPageTimeoutMs()));
                        } catch (PlaywrightException ignored) {}

                        String html = page.content();

                        if (offersVisited <= 3) {
                            log.info("AJB: sample html length for {} -> {}", offerUrl, html == null ? 0 : html.length());
                            log.info("AJB: page.url() after navigate -> {}", page.url());
                        }

                        Set<String> emails = emailExtractor.extractEmails(html);

                        if (emails.isEmpty()) {
                            log.info("AJB: offer {} -> no emails", offerUrl);
                            sleepJitter();
                            continue;
                        }

                        offersWithEmails++;
                        emailsExtracted += emails.size();

                        for (String raw : emails) {
                            if (raw == null || raw.isBlank()) continue;

                            String email = raw.trim().toLowerCase();
                            if (!uniqueRunEmails.add(email)) continue;
                            emailsUnique++;

                            if (farmLeadRepository.existsByEmailIgnoreCase(email)) {
                                emailsAlreadyInDb++;
                                continue;
                            }

                            if (props.isDryRun()) {
                                log.info("AJB: DRY RUN new email: {} (source={})", email, offerUrl);
                                continue;
                            }

                            FarmLead lead = FarmLead.builder()
                                    .email(email)
                                    .sourceUrl(offerUrl)
                                    .createdAt(LocalDateTime.now())
                                    .active(true)
                                    .bounce(false)
                                    .unsubscribeToken(TokenGenerator.generateShortToken())
                                    .build();

                            farmLeadRepository.save(lead);
                            leadsSaved++;
                        }

                        sleepJitter();

                    } catch (PlaywrightException e) {
                        log.warn("AJB: offer failed {} reason={}", offerUrl, e.getMessage());
                        sleepJitter();
                    } catch (Exception e) {
                        log.warn("AJB: offer failed {} error={}", offerUrl, e.getMessage(), e);
                        sleepJitter();
                    }
                }
            } finally {
                try { browser.close(); } catch (Exception ignored) {}
            }
        }

        AjbRunSummary summary = AjbRunSummary.builder()
                .dryRun(props.isDryRun())
                .offersCollected(offerUrls.size())
                .offersVisited(offersVisited)
                .offersWithEmails(offersWithEmails)
                .emailsExtracted(emailsExtracted)
                .emailsUnique(emailsUnique)
                .emailsAlreadyInDb(emailsAlreadyInDb)
                .leadsSaved(leadsSaved)
                .build();

        log.info("AJB SUMMARY: {}", summary.toLogLine());
        return summary;
    }

    private void sleepJitter() {
        int min = props.getMinDelayMs();
        int max = props.getMaxDelayMs();
        int delay = (max <= min) ? min : (min + (int) (Math.random() * (max - min + 1)));
        try { Thread.sleep(delay); }
        catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}
