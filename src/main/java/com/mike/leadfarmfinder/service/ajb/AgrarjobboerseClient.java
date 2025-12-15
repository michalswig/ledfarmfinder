package com.mike.leadfarmfinder.service.ajb;

import com.mike.leadfarmfinder.config.AgrarjobboerseProperties;
import com.microsoft.playwright.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static com.microsoft.playwright.options.LoadState.NETWORKIDLE;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgrarjobboerseClient {

    private static final String BASE = "https://www.agrarjobboerse.de";
    private static final String BOERSE_PREFIX = "/boerse/";
    private static final Pattern HELFER_RX = Pattern.compile("Helfer", Pattern.CASE_INSENSITIVE);

    private static final String JOBLIST_TABLE = "table#joblist";
    private static final String JOBLIST_OFFER_LINKS = "table#joblist a[href*='stellenangebote/']";

    private static final String APPLY_BTN_FORM = "form#boerseFilter input[type='submit'][value='Anzeigen']";
    private static final String APPLY_BTN_INPUT = "input[type='submit'][value='Anzeigen']";

    private final AgrarjobboerseProperties props;

    public Set<String> collectOfferUrls() {
        Set<String> offerUrls = new LinkedHashSet<>();

        try (Playwright pw = Playwright.create()) {
            Browser browser = pw.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(true)
                            .setArgs(java.util.List.of(
                                    "--no-sandbox",
                                    "--disable-dev-shm-usage"
                            ))
            );

            try (BrowserContext ctx = newContext(browser)) {
                Page page = ctx.newPage();

                page.setDefaultTimeout(props.getPageTimeoutMs());
                page.setDefaultNavigationTimeout(props.getPageTimeoutMs());

                page.navigate(
                        props.getStartUrl(),
                        new Page.NavigateOptions()
                                .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                );

                SelectStats stats = selectAllHelferFiltersByJs(page);
                log.info("AJB: helfer selected={}, skipped={}", stats.selected(), stats.skipped());

                clickApplyAnzeigenStable(page);

                int pagesVisited = 0;
                while (pagesVisited < props.getMaxPagesPerRun()
                        && offerUrls.size() < props.getMaxOffersPerRun()) {

                    pagesVisited++;

                    Set<String> pageUrls = extractOfferLinksFromJoblist(page);
                    int before = offerUrls.size();
                    offerUrls.addAll(pageUrls);

                    log.info(
                            "AJB: page {} -> +{} urls (total={})",
                            pagesVisited,
                            (offerUrls.size() - before),
                            offerUrls.size()
                    );

                    if (offerUrls.size() >= props.getMaxOffersPerRun()) break;
                    if (!goNextPageStable(page)) break;

                    waitForJoblistReady(page);
                    sleepJitter();
                }

            } finally {
                try { browser.close(); } catch (Exception ignored) {}
            }
        }

        return offerUrls.stream()
                .limit(props.getMaxOffersPerRun())
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    private BrowserContext newContext(Browser browser) {
        return browser.newContext(new Browser.NewContextOptions()
                .setUserAgent(
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                "Chrome/120.0.0.0 Safari/537.36"
                )
                .setLocale("de-DE")
                .setTimezoneId("Europe/Berlin")
        );
    }

    private void clickApplyAnzeigenStable(Page page) {
        Locator btn = findApplyButton(page);
        if (btn.count() == 0) {
            throw new IllegalStateException("AJB: cannot find APPLY button 'Anzeigen'");
        }

        log.info("AJB: clicking anzeigen... urlBefore={}", page.url());

        clickBestEffort(btn);

        // NETWORKIDLE bywa kapryśne / nieskończone — używamy tylko jako "bonus"
        try {
            page.waitForLoadState(NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(7000));
        } catch (PlaywrightException ignored) {}

        // warunek gotowości: linki ofert (to jest Twój realny cel)
        waitForJoblistReady(page);

        int links = page.locator(JOBLIST_OFFER_LINKS).count();
        log.info("AJB: after anzeigen -> urlAfter={} joblist links={}", page.url(), links);
    }

    private Locator findApplyButton(Page page) {
        Locator btn = page.locator(APPLY_BTN_FORM);
        if (btn.count() == 0) btn = page.locator(APPLY_BTN_INPUT);
        if (btn.count() == 0) btn = page.getByText("Anzeigen", new Page.GetByTextOptions().setExact(true));
        return btn;
    }

    private void clickBestEffort(Locator btn) {
        try {
            btn.first().click(new Locator.ClickOptions().setTimeout(props.getClickTimeoutMs()));
        } catch (PlaywrightException e) {
            btn.first().click(new Locator.ClickOptions()
                    .setTimeout(props.getClickTimeoutMs())
                    .setForce(true));
        }
    }

    private void waitForJoblistReady(Page page) {
        try {
            page.waitForSelector(JOBLIST_TABLE,
                    new Page.WaitForSelectorOptions().setTimeout(props.getPageTimeoutMs()));

            page.waitForSelector(JOBLIST_OFFER_LINKS,
                    new Page.WaitForSelectorOptions().setTimeout(props.getPageTimeoutMs()));
        } catch (PlaywrightException e) {
            logAjbDebug(page, "waitForJoblistReady failed", e);
            throw e;
        }
    }

    private Set<String> extractOfferLinksFromJoblist(Page page) {
        Locator links = page.locator(JOBLIST_OFFER_LINKS);
        int count = links.count();

        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            String href = links.nth(i).getAttribute("href");
            if (href == null || href.isBlank()) continue;
            result.add(normalizeOfferUrl(href));
        }

        log.info("AJB: extracted offer links from joblist: {}", result.size());
        return result;
    }

    private String normalizeOfferUrl(String href) {
        String h = href.trim();

        if (h.startsWith("http://") || h.startsWith("https://")) {
            if (h.startsWith(BASE + "/stellenangebote/")) {
                return BASE + BOERSE_PREFIX + h.substring((BASE + "/").length());
            }
            return h;
        }

        if (h.startsWith("/")) {
            if (h.startsWith("/stellenangebote/")) {
                return BASE + BOERSE_PREFIX + h.substring(1);
            }
            return BASE + h;
        }

        if (h.startsWith("stellenangebote/")) {
            return BASE + BOERSE_PREFIX + h;
        }

        return BASE + "/" + h;
    }

    private boolean goNextPageStable(Page page) {
        Locator nextRel = page.locator("div.nav_page a[rel='next']");
        if (nextRel.count() > 0) {
            nextRel.first().click(new Locator.ClickOptions().setTimeout(props.getClickTimeoutMs()));
        } else {
            Locator next = page.locator("text=»");
            if (next.count() == 0) next = page.locator("text=Weiter");
            if (next.count() == 0) return false;

            next.first().click(new Locator.ClickOptions().setTimeout(props.getClickTimeoutMs()));
        }

        // opcjonalnie: krótkie NETWORKIDLE, ale nie blokujemy
        try {
            page.waitForLoadState(NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(7000));
        } catch (PlaywrightException ignored) {}

        return true;
    }

    private SelectStats selectAllHelferFiltersByJs(Page page) {
        Locator labels = page.locator("label", new Page.LocatorOptions().setHasText(HELFER_RX));
        int count = labels.count();
        log.info("AJB: found {} filters containing 'Helfer'", count);

        int selected = 0;
        int skipped = 0;

        for (int i = 0; i < count; i++) {
            Locator label = labels.nth(i);
            String forId = label.getAttribute("for");
            if (forId == null || forId.isBlank()) continue;

            boolean ok = setCheckboxCheckedByJs(page, forId);
            if (ok) selected++;
            else {
                skipped++;
                log.warn("AJB: skip helfer forId={} (JS set checked failed)", forId);
            }
        }

        return new SelectStats(selected, skipped);
    }

    private boolean setCheckboxCheckedByJs(Page page, String id) {
        String js =
                "(id) => {" +
                        "  const el = document.getElementById(id);" +
                        "  if (!el) return false;" +
                        "  el.checked = true;" +
                        "  el.dispatchEvent(new Event('input', { bubbles: true }));" +
                        "  el.dispatchEvent(new Event('change', { bubbles: true }));" +
                        "  return el.checked === true;" +
                        "}";

        Object res = page.evaluate(js, id);
        if (res == null) return false;
        if (res instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(res));
    }

    private void logAjbDebug(Page page, String msg, Exception e) {
        try {
            String title = safe(page::title);
            String url = safe(page::url);
            String html = safe(page::content);

            int len = (html == null) ? 0 : html.length();
            String head = (html == null) ? "" : html.substring(0, Math.min(800, len));

            log.error("AJB DEBUG: {} | url={} | title='{}' | htmlLen={} | head={}",
                    msg, url, title, len, head, e);
        } catch (Exception ignored) {
            log.error("AJB DEBUG: {} (and failed to read page state)", msg, e);
        }
    }

    private String safe(java.util.concurrent.Callable<String> c) {
        try { return c.call(); } catch (Exception e) { return "N/A"; }
    }

    private void sleepJitter() {
        int min = props.getMinDelayMs();
        int max = props.getMaxDelayMs();
        int delay = (max <= min) ? min : (min + (int) (Math.random() * (max - min + 1)));
        try { Thread.sleep(delay); }
        catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    private record SelectStats(int selected, int skipped) {}
}
