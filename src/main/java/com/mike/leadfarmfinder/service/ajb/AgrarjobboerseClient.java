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
    private static final String JOBLIST_ROWS = "table#joblist tbody tr";
    // UWAGA: na stronie href bywa "stellenangebote/231639_..." (bez /boerse/)
    private static final String JOBLIST_OFFER_LINKS = "table#joblist a[href*='stellenangebote/']";

    private final AgrarjobboerseProperties props;

    public Set<String> collectOfferUrls() {
        Set<String> offerUrls = new LinkedHashSet<>();

        try (Playwright pw = Playwright.create()) {
            Browser browser = pw.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(true)
                            // Render / Docker – wymagane
                            .setArgs(java.util.List.of(
                                    "--no-sandbox",
                                    "--disable-dev-shm-usage"
                            ))
            );

            try (BrowserContext ctx = browser.newContext()) {
                Page page = ctx.newPage();
                page.setDefaultTimeout(props.getPageTimeoutMs());

                page.navigate(
                        props.getStartUrl(),
                        new Page.NavigateOptions()
                                .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                );

                // Jeśli startUrl ma bkz=... to zazwyczaj i tak już jest ustawione,
                // ale selekcja po labelach "Helfer" nic nie psuje
                SelectStats stats = selectAllHelferFiltersByJs(page);
                log.info("AJB: helfer selected={}, skipped={}", stats.selected(), stats.skipped());

                clickApplyAnzeigen(page);

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
                    if (!goNextPage(page)) break;

                    waitForJoblistRows(page);
                    sleepJitter();
                }
            } finally {
                try {
                    browser.close();
                } catch (Exception ignored) {
                }
            }
        }


        return offerUrls.stream()
                .limit(props.getMaxOffersPerRun())
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    private void clickApplyAnzeigen(Page page) {
        Locator btn = page.locator("form#boerseFilter input[type='submit'][value='Anzeigen']");
        if (btn.count() == 0) btn = page.locator("input[type='submit'][value='Anzeigen']");
        if (btn.count() == 0) btn = page.getByText("Anzeigen", new Page.GetByTextOptions().setExact(true));

        if (btn.count() == 0) {
            throw new IllegalStateException("AJB: cannot find APPLY button 'Anzeigen'");
        }

        log.info("AJB: clicking anzeigen...");

        try {
            btn.first().click(new Locator.ClickOptions().setTimeout(props.getClickTimeoutMs()));
        } catch (PlaywrightException e1) {
            btn.first().click(new Locator.ClickOptions().setTimeout(props.getClickTimeoutMs()).setForce(true));
        }

        page.waitForLoadState(NETWORKIDLE);
        waitForJoblistRows(page);

        int links = page.locator(JOBLIST_OFFER_LINKS).count();
        log.info("AJB: after anzeigen -> joblist links={}", links);
    }

    private void waitForJoblistRows(Page page) {
        page.waitForSelector(JOBLIST_TABLE, new Page.WaitForSelectorOptions().setTimeout(props.getPageTimeoutMs()));

        // Nie "visible", tylko "istnieją wiersze" – stabilniejsze w headless
        String fn =
                "() => {" +
                        "  const rows = document.querySelectorAll('table#joblist tbody tr');" +
                        "  return rows && rows.length > 0;" +
                        "}";

        page.waitForFunction(fn, null, new Page.WaitForFunctionOptions().setTimeout(props.getPageTimeoutMs()));
    }

    private Set<String> extractOfferLinksFromJoblist(Page page) {
        Locator links = page.locator(JOBLIST_OFFER_LINKS);
        int count = links.count();

        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            String href = links.nth(i).getAttribute("href");
            if (href == null || href.isBlank()) continue;

            String normalized = normalizeOfferUrl(href);
            result.add(normalized);
        }

        log.info("AJB: extracted offer links from joblist: {}", result.size());
        return result;
    }

    /**
     * Najważniejsza poprawka: href na stronie bywa "stellenangebote/231639_..."
     * i wtedy poprawny URL to BASE + "/boerse/" + href
     */
    private String normalizeOfferUrl(String href) {
        String h = href.trim();

        // już absolutny
        if (h.startsWith("http://") || h.startsWith("https://")) {
            // poprawka na przypadek, gdy absolutny ale bez /boerse/
            if (h.startsWith(BASE + "/stellenangebote/")) {
                return BASE + BOERSE_PREFIX + h.substring((BASE + "/").length());
            }
            return h;
        }

        // zaczyna się od /
        if (h.startsWith("/")) {
            // /stellenangebote/... -> /boerse/stellenangebote/...
            if (h.startsWith("/stellenangebote/")) {
                return BASE + BOERSE_PREFIX + h.substring(1);
            }
            return BASE + h;
        }

        // względny typu "stellenangebote/..."
        if (h.startsWith("stellenangebote/")) {
            return BASE + BOERSE_PREFIX + h;
        }

        // fallback
        return BASE + "/" + h;
    }

    private boolean goNextPage(Page page) {
        Locator nextRel = page.locator("div.nav_page a[rel='next']");
        if (nextRel.count() > 0) {
            nextRel.first().click(new Locator.ClickOptions().setTimeout(props.getClickTimeoutMs()));
            page.waitForLoadState(NETWORKIDLE);
            return true;
        }

        Locator next = page.locator("text=»");
        if (next.count() == 0) next = page.locator("text=Weiter");
        if (next.count() == 0) return false;

        next.first().click(new Locator.ClickOptions().setTimeout(props.getClickTimeoutMs()));
        page.waitForLoadState(NETWORKIDLE);
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

    private void sleepJitter() {
        int min = props.getMinDelayMs();
        int max = props.getMaxDelayMs();
        int delay = (max <= min) ? min : (min + (int) (Math.random() * (max - min + 1)));
        try { Thread.sleep(delay); }
        catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    private record SelectStats(int selected, int skipped) {}
}
