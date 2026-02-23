package com.mike.leadfarmfinder.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailExtractor {

    private final MxLookUp mxLookUp;

    /**
     * Prosty regex na "normalne" maile (po normalizacji obfuskacji).
     */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    /**
     * Mailto: mailto:info@domain.de lub mailto:info(at)domain(dot)de?subject=...
     */
    private static final Pattern MAILTO_PATTERN =
            Pattern.compile("(?i)mailto:([^\"'\\s>]+)");

    /**
     * Cloudflare obfuscation: data-cfemail="..."
     */
    private static final Pattern CLOUDFLARE_PATTERN =
            Pattern.compile("data-cfemail=\"([0-9a-fA-F]+)\"");

    /**
     * TLD allow-list.
     */
    private static final Set<String> ALLOWED_TLDS = Set.of("de", "com", "net", "eu");

    /**
     * Lokalna część e-maila (przed @) – ASCII, 2–40.
     */
    private static final Pattern LOCAL_PART_PATTERN =
            Pattern.compile("^[a-z0-9._%+-]{2,40}$");

    /**
     * Pseudo-unicode typu u00fc w local-part.
     */
    private static final Pattern SUSPICIOUS_UNICODE_ESCAPE =
            Pattern.compile("u00[0-9a-fA-F]{2}");

    /**
     * FIX: telefon + mail sklejone: "... 68diegaertnerei@..." albo "0176...567mona@..."
     * Ucinamy wiodące >=2 cyfry, jeśli zaraz po nich jest litera.
     */
    private static final Pattern LEADING_PHONE_DIGITS_BEFORE_LETTER =
            Pattern.compile("^\\d{2,}(?=[a-z])");

    /**
     * Śmieci na końcu maila w HTML: ),.;:'"> itp.
     */
    private static final Pattern TRAILING_JUNK =
            Pattern.compile("[\\)\\]\\}\\.,;:'\"<>]+$");

    // =========================
    // Config (feature flags)
    // =========================

    @Value("${leadfinder.email.mx-check:true}")
    private boolean mxCheckEnabled;

    @Value("${leadfinder.email.mx-unknown-policy:ALLOW}")
    private String mxUnknownPolicy;

    // =========================
    // Public API
    // =========================

    public Set<String> extractEmails(String html) {
        Set<String> results = new LinkedHashSet<>();
        if (html == null || html.isBlank()) return results;

        // 0) Normalizacja (at)/(dot) na całym HTML
        String normalizedHtml = normalizeObfuscatedEmailsInText(html);

        // 1) Cloudflare data-cfemail
        Matcher cf = CLOUDFLARE_PATTERN.matcher(normalizedHtml);
        while (cf.find()) {
            String decoded = CloudflareEmailDecoder.decode(cf.group(1));
            if (decoded == null) continue;

            String normalized = normalizeEmail(decoded);
            if (normalized != null) results.add(normalized);
        }

        // 2) Mailto links
        Matcher mailto = MAILTO_PATTERN.matcher(normalizedHtml);
        while (mailto.find()) {
            String raw = mailto.group(1);

            int q = raw.indexOf('?');
            if (q >= 0) raw = raw.substring(0, q);

            raw = normalizeObfuscatedEmailsInText(raw);

            String normalized = normalizeEmail(raw);
            if (normalized != null) results.add(normalized);
        }

        // 3) Zwykłe maile w tekście/HTML
        Matcher matcher = EMAIL_PATTERN.matcher(normalizedHtml);
        while (matcher.find()) {
            String normalized = normalizeEmail(matcher.group());
            if (normalized != null) results.add(normalized);
        }

        return results;
    }

    // =========================
    // Normalization helpers
    // =========================

    String normalizeObfuscatedEmailsInText(String input) {
        if (input == null || input.isBlank()) return input;

        String s = input;

        s = s.replaceAll("(?i)\\s*[\\(\\[\\{<]\\s*at\\s*[\\)\\]\\}>]\\s*", "@");
        s = s.replaceAll("(?i)\\s*[\\(\\[\\{<]\\s*dot\\s*[\\)\\]\\}>]\\s*", ".");

        // warianty " at " / " dot "
        s = s.replaceAll("(?i)\\s+at\\s+", "@");
        s = s.replaceAll("(?i)\\s+dot\\s+", ".");

        return s;
    }

    String normalizeEmail(String raw) {
        if (raw == null) return null;

        String email = raw;

        // URL decode
        try {
            email = URLDecoder.decode(email, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
        }

        email = email.trim();
        if (email.isEmpty()) return null;

        // usuń śmieci z początku i końca (HTML)
        email = email.replaceAll("^[\"'<>()\\[\\];:,]+", "").trim();
        email = TRAILING_JUNK.matcher(email).replaceAll("").trim();
        if (email.isEmpty()) return null;

        // usuń wiodące %xx
        while (email.matches("^%[0-9A-Fa-f]{2}.*")) {
            email = email.substring(3).trim();
        }
        if (email.isEmpty()) return null;

        // start musi być alnum
        char first = email.charAt(0);
        if (!Character.isLetterOrDigit(first)) return null;

        int atIndex = email.indexOf('@');
        int lastDot = email.lastIndexOf('.');
        if (atIndex <= 0 || lastDot <= atIndex) return null;

        // drugi '@' -> out
        if (email.indexOf('@', atIndex + 1) != -1) return null;

        // --- split ---
        String localPart = email.substring(0, atIndex).trim().toLowerCase();
        String hostWithoutTld = email.substring(atIndex + 1, lastDot).trim();
        String tldPart = email.substring(lastDot + 1).trim();

        // FIX: telefon + mail sklejone
        localPart = LEADING_PHONE_DIGITS_BEFORE_LETTER.matcher(localPart).replaceFirst("");
        if (localPart.isBlank()) return null;

        // validate local
        if (!isLocalPartAllowed(localPart)) return null;

        // validate host (bez TLD)
        if (!isHostWithoutTldAllowed(hostWithoutTld)) return null;

        // known tld
        String tld = extractKnownTld(tldPart);
        if (tld == null) return null;

        String domain = hostWithoutTld.toLowerCase() + "." + tld;

        if (mxCheckEnabled) {
            MxLookUp.MxStatus mx = mxLookUp.checkDomain(domain);

            if (mx == MxLookUp.MxStatus.INVALID) return null;

            if (mx == MxLookUp.MxStatus.UNKNOWN) {
                boolean drop = "DROP".equalsIgnoreCase(mxUnknownPolicy);
                if (drop) return null;
                log.warn("MX check UNKNOWN for domain={}, email={}", domain, raw);
            }
        }
        return localPart + "@" + domain;
    }

    private String extractKnownTld(String tldPart) {
        if (tldPart == null || tldPart.isEmpty()) return null;

        String lower = tldPart.toLowerCase();
        for (String allowed : ALLOWED_TLDS) {
            if (lower.equals(allowed) || lower.startsWith(allowed)) {
                return allowed;
            }
        }
        return null;
    }

    private boolean isLocalPartAllowed(String localPartRaw) {
        if (localPartRaw == null) return false;

        String local = localPartRaw.trim().toLowerCase();
        if (SUSPICIOUS_UNICODE_ESCAPE.matcher(local).find()) return false;

        return LOCAL_PART_PATTERN.matcher(local).matches();
    }

    private boolean isHostWithoutTldAllowed(String hostWithoutTldRaw) {
        if (hostWithoutTldRaw == null) return false;

        String h = hostWithoutTldRaw.trim();
        if (h.isBlank()) return false;

        // Bez spacji i niedozwolonych znaków
        // (host może mieć litery, cyfry, kropki i myślniki)
        if (!h.matches("^[a-zA-Z0-9.-]+$")) return false;

        // Nie zaczynaj / nie kończ kropką lub myślnikiem
        if (h.startsWith(".") || h.endsWith(".")) return false;
        if (h.startsWith("-") || h.endsWith("-")) return false;

        // Bez podwójnych kropek
        if (h.contains("..")) return false;

        return true;
    }

}
