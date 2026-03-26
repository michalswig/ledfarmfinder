package com.mike.leadfarmfinder.service.discovery;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class DiscoveryUrlScorer {

    private final DiscoveryUrlNormalizer urlNormalizer;
    private final DiscoveryUrlFilter urlFilter;

    private static final List<String> FARM_KEYWORDS = List.of(
            "hof", "hofladen",
            "obst", "obsthof",
            "gemuese", "gemüse",
            "erdbeer", "beeren", "himbeer", "beerenhof",
            "spargel",
            "kartoffel", "kartoffelhof",
            "ackerbau", "getreide", "getreidehof",
            "landwirtschaft", "bauernhof", "landhof",
            "gaertnerei", "gärtnerei", "gartenbau",
            "blumen", "schnittblumen",
            "weingut", "winzer",
            "biohof", "bioland", "demeter",
            "gewaechshaus", "gewächshaus",
            "jungpflanzen", "pflanzen",
            "baumschule", "stauden",
            "pilz", "pilze", "pilzzucht", "champignon",
            "gemuesebau", "gemüsebau",
            "garten", "spargelhof",
            "betrieb", "familienbetrieb",
            "direktvermarktung", "hofverkauf",
            "verarbeitung", "aufbereitung", "pack", "verpackung", "sortierung", "lager",
            "milchvieh", "vieh", "rinder", "schwein", "gefluegel", "geflügel", "eier", "legehennen"
    );

    private static final List<String> SOFT_NEGATIVE_DOMAIN_TOKENS = List.of(
            "wordpress",
            "wix",
            "jimdo",
            "ionos",
            "strato",
            "webnode",
            "joomla",
            "hosting"
    );

    private static final List<String> URL_HINT_KEYWORDS = List.of(
            "/kontakt", "/contact",
            "/impressum",
            "/datenschutz",
            "/ueber-uns", "/uber-uns", "/über-uns",
            "/betrieb", "/unternehmen",
            "/hofladen", "/hofverkauf"
    );

    public int computeDomainPriorityScore(String url) {
        String domain = urlNormalizer.extractNormalizedDomain(url);
        if (domain == null) {
            return 0;
        }

        if (urlFilter.isHardNegative(domain)) {
            return -100;
        }

        int score = 0;

        for (String keyword : FARM_KEYWORDS) {
            if (domain.contains(keyword)) {
                score += 20;
            }
        }

        if (domain.endsWith(".de")) {
            score += 10;
        }

        if (domain.length() <= 15) {
            score += 5;
        }

        if (domain.contains("shop") || domain.contains("markt") || domain.contains("portal")) {
            score -= 5;
        }

        for (String softNegative : SOFT_NEGATIVE_DOMAIN_TOKENS) {
            if (domain.contains(softNegative)) {
                score -= 3;
            }
        }

        String lowerUrl = url.toLowerCase(Locale.ROOT);
        for (String hint : URL_HINT_KEYWORDS) {
            if (lowerUrl.contains(hint)) {
                score += 8;
            }
        }

        return score;
    }

    public boolean looksLikeFarmDomain(String domain) {
        String lowerDomain = domain.toLowerCase(Locale.ROOT);
        if (urlFilter.isHardNegative(lowerDomain)) {
            return false;
        }
        return hasFarmKeyword(lowerDomain);
    }

    public boolean hasFarmKeyword(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : FARM_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}