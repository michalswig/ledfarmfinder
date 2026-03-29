package com.mike.leadfarmfinder.service.discovery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DiscoveryUrlFilter {

    private final DiscoveryUrlNormalizer urlNormalizer;

    private static final Set<String> BLOCKED_DOMAINS = Set.of(
            "de.indeed.com",
            "facebook.com",
            "indeed.com",
            "instagram.com",
            "linkedin.com",
            "meinestadt.de",
            "stepstone.de",
            "tiktok.com",
            "xing.com",
            "youtube.com",
            "sh-tourismus.de",
            "ard.de",
            "br.de",
            "hr.de",
            "mdr.de",
            "ndr.de",
            "rtl.de",
            "swr.de",
            "wdr.de",
            "zdf.de",
            "airbnb.com",
            "airbnb.de",
            "booking.com",
            "kleinanzeigen.de",
            "obstbaufachbetriebe.de"
    );

    private static final List<String> HARD_NEGATIVE_PATH_TOKENS = List.of(
            "/ratgeber",
            "/blog",
            "/magazin",
            "/news",
            "/presse",
            "/artikel",
            "/article",
            "/report",
            "/wiki",
            "/lexikon",
            "/kategorie",
            "/category",
            "/tag/",
            "/tags/",
            "/author/",
            "/job", "/jobs", "/stellen", "/karriere",
            "/anbieter",
            "/anbieterverzeichnis",
            "/verzeichnis",
            "/liste",
            "/listen",
            "/uebersicht",
            "/übersicht",
            "/karte",
            "/map",
            "/region",
            "/tourismus",
            "/urlaub",
            "/freizeit",
            "/verwaltung/",
            "/dienstleistungen/",
            "/serviceassistent/",
            "/leistung/",
            "/download",
            "/attachment/",
            "/dokumente/",
            "/informationen/",
            "/einheit.php",
            "?dl=",
             "mam/cms",
             "/fileadmin/"
    );

    private static final List<String> HARD_NEGATIVE_KEYWORDS = List.of(
            "bundeskanzler",
            "bundesregierung",
            "ministerium",
            "regierung",
            "landtag",
            "verwaltung",
            "rathaus",
            "stadt-",
            "gemeinde-",
            "kreis-",
            "landkreis",
            "destatis",
            "statista",
            "statistik",
            "hochschule",
            "uni-",
            "universitaet",
            "universität",
            "fh-",
            "greenpeace.",
            "nabu.",
            "wwf.",
            "verbraucherzentrale",
            "verbraucherzentralen",
            "ec.europa",
            "europa.eu",
            "bauernverband",
            "ble.de",
            "bzfe.de",
            "handelskammer",
            "kammer",
            "landwirtschaft-bw.de",
            "lwk-niedersachsen.de",
            "dw.com",
            "deutschlandfunk",
            "deutsche-welle",
            "faz",
            "focus",
            "merkur",
            "morgenpost",
            "rbb",
            "sueddeutsche",
            "spiegel",
            "stern",
            "t-online",
            "tagesschau",
            "tagesthemen",
            "welt",
            "zeit",
            "zdf",
            "wdr",
            "swr",
            "ndr",
            "mdr",
            "hr",
            "br",
            "ard",
            "bild",
            "tourism",
            "tourismus",
            "touristik",
            "reiseland",
            "reisefuhrer",
            "reiseführer",
            "urlaub",
            "visit",
            "freizeit",
            "ausflug",
            "erleben",
            "stadtmarketing",
            "branchenbuch",
            "gelbeseiten",
            "marktplatz",
            "verzeichnis",
            "portal",
            "cylex",
            "golocal",
            "yelp",
            "11880",
            "trustedshops",
            "werliefertwas",
            "airbnb",
            "booking",
            "ferienwohnung",
            "ferienwohnungen",
            "ferienhof",
            "bauernhofurlaub",
            "urlaub-auf-dem-bauernhof",
            "ferienhaus",
            "ferienhaeuser",
            "ferienhäuser",
            "pension",
            "gasthof",
            "hotel",
            "zimmer",
            "zimmervermietung",
            "camping",
            "zeltplatz",
            "stellplatz",
            "wohnmobil",
            "glamping",
            "tiny-house",
            "tiny-house-dorf",
            "wellness",
            "sauna",
            "zeitarbeit",
            "zeitarbeitsfirma",
            "personalvermittlung",
            "personaldienstleister",
            "arbeitsagentur",
            "jobvermittlung",
            "leiharbeit",
            "arbeitnehmerüberlassung",
            "obstbaufachbetriebe"
    );

    public boolean isAllowedDomain(String url) {
        String domain = urlNormalizer.extractNormalizedDomain(url);
        if (domain == null) {
            log.info("DiscoveryUrlFilter: dropping url={} (no domain)", url);
            return false;
        }

        if (BLOCKED_DOMAINS.contains(domain)) {
            log.info("DiscoveryUrlFilter: dropping url={} (blocked domain={})", url, domain);
            return false;
        }

        if (isHardNegative(domain)) {
            log.info("DiscoveryUrlFilter: dropping url={} (hard-negative domain={})", url, domain);
            return false;
        }

        if (domain.contains("zeitung") || domain.contains("news")) {
            log.info("DiscoveryUrlFilter: dropping url={} (looks like news/media domain={})", url, domain);
            return false;
        }

        return true;
    }

    public boolean isHardNegativePath(String url) {
        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            if (path == null) {
                return false;
            }

            String lowerPath = path.toLowerCase(Locale.ROOT);
            for (String token : HARD_NEGATIVE_PATH_TOKENS) {
                if (lowerPath.contains(token)) {
                    return true;
                }
            }
            return false;

        } catch (Exception e) {
            return false;
        }
    }

    public boolean isHardNegative(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : HARD_NEGATIVE_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}