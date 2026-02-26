package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.config.EmailExtractorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final EmailExtractorProperties props;

    /**
     * Simple regex for "normal" emails (after de-obfuscation / normalization).
     */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    /**
     * Matches mailto links, e.g.:
     * - mailto:info@domain.de
     * - mailto:info(at)domain(dot)de?subject=...
     *
     * Captures everything after "mailto:" up to a quote/whitespace/> character.
     * The (?i) flag makes it case-insensitive (MAILTO, Mailto, etc.).
     */
    private static final Pattern MAILTO_PATTERN =
            Pattern.compile("(?i)mailto:([^\"'\\s>]+)");

    /**
     * Cloudflare email obfuscation attribute: data-cfemail="..."
     * The group captures the hex payload which can be decoded to a real email address.
     */
    private static final Pattern CLOUDFLARE_PATTERN =
            Pattern.compile("data-cfemail=\"([0-9a-fA-F]+)\"");

    /**
     * Allowed top-level domains (TLD allow-list).
     * Keeps only emails ending with one of these TLDs.
     */
    private static final Set<String> ALLOWED_TLDS = Set.of("de", "com", "net", "eu");

    /**
     * Email local-part (before '@') validation:
     * - ASCII only
     * - length 2–40
     * - allowed chars: a-z, 0-9, dot, underscore, percent, plus, minus
     *
     * NOTE: This is intentionally strict (not fully RFC 5322-compliant),
     * optimized for lead extraction quality.
     */
    private static final Pattern LOCAL_PART_PATTERN =
            Pattern.compile("^[a-z0-9._%+-]{2,40}$");

    /**
     * Detects pseudo-unicode escape sequences like "u00fc" inside the local-part.
     * These often indicate mangled/obfuscated text rather than a real email.
     */
    private static final Pattern SUSPICIOUS_UNICODE_ESCAPE =
            Pattern.compile("u00[0-9a-fA-F]{2}");

    /**
     * FIX: phone number glued to an email, e.g.:
     * - "... 68diegaertnerei@..."
     * - "0176...567mona@..."
     *
     * Removes leading 2+ digits if they are immediately followed by a letter.
     */
    private static final Pattern LEADING_PHONE_DIGITS_BEFORE_LETTER =
            Pattern.compile("^\\d{2,}(?=[a-z])");

    /**
     * Trailing junk characters commonly found in HTML/text scraping, e.g.:
     * ), . , ; : ' " < >
     *
     * Strips these from the end of a candidate email string.
     */
    private static final Pattern TRAILING_JUNK =
            Pattern.compile("[\\)\\]\\}\\.,;:'\"<>]+$");

    // =========================
    // Public API
    // =========================

    public Set<String> extractEmails(String html) {
        Set<String> results = new LinkedHashSet<>();
        if (html == null || html.isBlank()) return results;

        // 0) Normalize common obfuscations like (at)/(dot) across the whole HTML
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

        // 3) Plain emails found inside text/HTML
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

        // Variants like " at " / " dot "
        s = s.replaceAll("(?i)\\s+at\\s+", "@");
        s = s.replaceAll("(?i)\\s+dot\\s+", ".");

        return s;
    }

    String normalizeEmail(String raw) {
        if (raw == null) return null;

        String email = raw;
        if (raw.contains("%")) {
            try {
                email = URLDecoder.decode(raw, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ex) {
                log.debug("URL decode failed for email candidate: '{}'", raw, ex);
                email = raw;
            }
        }

        email = email.trim();
        if (email.isEmpty()) return null;

        // Strip common HTML/text junk from start/end
        email = email.replaceAll("^[\"'<>()\\[\\];:,]+", "").trim();
        email = TRAILING_JUNK.matcher(email).replaceAll("").trim();
        if (email.isEmpty()) return null;

        // Strip leading %xx sequences (leftovers from broken URL encoding)
        while (email.matches("^%[0-9A-Fa-f]{2}.*")) {
            email = email.substring(3).trim();
        }
        if (email.isEmpty()) return null;

        // Must start with an alphanumeric character
        char first = email.charAt(0);
        if (!Character.isLetterOrDigit(first)) return null;

        int atIndex = email.indexOf('@');
        int lastDot = email.lastIndexOf('.');
        if (atIndex <= 0 || lastDot <= atIndex) return null;

        // Second '@' -> reject
        if (email.indexOf('@', atIndex + 1) != -1) return null;

        // --- split ---
        String localPart = email.substring(0, atIndex).trim().toLowerCase();
        String hostWithoutTld = email.substring(atIndex + 1, lastDot).trim();
        String tldPart = email.substring(lastDot + 1).trim();

        // FIX: phone number glued to email
        localPart = LEADING_PHONE_DIGITS_BEFORE_LETTER.matcher(localPart).replaceFirst("");
        if (localPart.isBlank()) return null;

        // Validate local-part
        if (!isLocalPartAllowed(localPart)) return null;

        // Validate host (without TLD)
        if (!isHostWithoutTldAllowed(hostWithoutTld)) return null;

        // Validate/normalize TLD against allow-list
        String tld = extractKnownTld(tldPart);
        if (tld == null) return null;

        String domain = hostWithoutTld.toLowerCase() + "." + tld;

        // Optional MX check
        if (props.mxCheckEnabled()) {
            MxLookUp.MxStatus mx = mxLookUp.checkDomain(domain);

            if (mx == MxLookUp.MxStatus.INVALID) return null;

            if (mx == MxLookUp.MxStatus.UNKNOWN) {
                boolean drop = "DROP".equalsIgnoreCase(props.mxUnknownPolicy());
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

        // No spaces and no forbidden characters
        // (host may contain letters, digits, dots, and hyphens)
        if (!h.matches("^[a-zA-Z0-9.-]+$")) return false;

        // Must not start/end with a dot or hyphen
        if (h.startsWith(".") || h.endsWith(".")) return false;
        if (h.startsWith("-") || h.endsWith("-")) return false;

        // No double dots
        if (h.contains("..")) return false;

        return true;
    }

}