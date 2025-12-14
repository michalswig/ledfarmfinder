package com.mike.leadfarmfinder.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.naming.NamingException;
import javax.naming.directory.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailExtractor {

    // =========================
    // Patterns
    // =========================

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

    // =========================
    // Config (feature flags)
    // =========================

    /**
     * MX check on/off. Domyślnie true.
     */
    @Value("${leadfinder.email.mx-check:true}")
    private boolean mxCheckEnabled;

    /**
     * Co robić, gdy MX check nie może być wykonany (timeout/błąd DNS)?
     * ALLOW = przepuść (większy zasięg)
     * DROP  = odrzuć (bardziej "twarda" jakość kosztem zasięgu)
     */
    @Value("${leadfinder.email.mx-unknown-policy:ALLOW}")
    private String mxUnknownPolicy;

    /**
     * Timeout DNS (ms) dla JNDI.
     */
    @Value("${leadfinder.email.mx-timeout-ms:2000}")
    private long mxTimeoutMs;

    private enum MxStatus { VALID, INVALID, UNKNOWN }

    // =========================
    // Public API
    // =========================

    public Set<String> extractEmails(String html) {
        Set<String> results = new LinkedHashSet<>();

        if (html == null || html.isBlank()) {
            return results;
        }

        // 0) Normalizacja (at)/(dot) na całym HTML
        String normalizedHtml = normalizeObfuscatedEmailsInText(html);

        // 1) Cloudflare data-cfemail
        Matcher cf = CLOUDFLARE_PATTERN.matcher(normalizedHtml);
        while (cf.find()) {
            String encoded = cf.group(1);
            String decoded = CloudflareEmailDecoder.decode(encoded);
            if (decoded == null) continue;

            String normalized = normalizeEmail(decoded);
            if (normalized != null) results.add(normalized);
        }

        // 2) Mailto links (często kluczowe)
        Matcher mailto = MAILTO_PATTERN.matcher(normalizedHtml);
        while (mailto.find()) {
            String raw = mailto.group(1);

            // usuń parametry po "?" np. ?subject=...
            int q = raw.indexOf('?');
            if (q >= 0) raw = raw.substring(0, q);

            // po mailto też przejedź normalizacją obfuskacji (dla mailto:info(at)dom(dot)de)
            raw = normalizeObfuscatedEmailsInText(raw);

            String normalized = normalizeEmail(raw);
            if (normalized != null) results.add(normalized);
        }

        // 3) Zwykłe maile w tekście/HTML
        Matcher matcher = EMAIL_PATTERN.matcher(normalizedHtml);
        while (matcher.find()) {
            String raw = matcher.group();
            String normalized = normalizeEmail(raw);
            if (normalized != null) results.add(normalized);
        }

        return results;
    }

    // =========================
    // Normalization helpers
    // =========================

    /**
     * Zamienia obfuskacje typu:
     * - (at), [at], {at}, <at>  -> @
     * - (dot), [dot], {dot}, <dot> -> .
     * - " at " -> @, " dot " -> . (opcjonalne, ale częste)
     */
    private String normalizeObfuscatedEmailsInText(String input) {
        if (input == null || input.isBlank()) return input;

        String s = input;

        s = s.replaceAll("(?i)\\s*[\\(\\[\\{<]\\s*at\\s*[\\)\\]\\}>]\\s*", "@");
        s = s.replaceAll("(?i)\\s*[\\(\\[\\{<]\\s*dot\\s*[\\)\\]\\}>]\\s*", ".");

        // warianty " at " / " dot "
        s = s.replaceAll("(?i)\\s+at\\s+", "@");
        s = s.replaceAll("(?i)\\s+dot\\s+", ".");

        return s;
    }

    /**
     * Normalize email:
     * - URL decode
     * - trim
     * - clean prefix junk
     * - split local@domain
     * - local-part validation
     * - known TLD extraction
     * - optional MX check
     */
    private String normalizeEmail(String raw) {
        if (raw == null) return null;

        String email = raw;

        try {
            email = URLDecoder.decode(email, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {}

        email = email.trim();
        if (email.isEmpty()) return null;

        email = email.replaceAll("^[\"'<>()\\[\\];:,]+", "").trim();

        while (email.matches("^%[0-9A-Fa-f]{2}.*")) {
            email = email.substring(3).trim();
        }
        if (email.isEmpty()) return null;

        char first = email.charAt(0);
        if (!Character.isLetterOrDigit(first)) return null;

        int atIndex = email.indexOf('@');
        int lastDot = email.lastIndexOf('.');
        if (atIndex <= 0 || lastDot <= atIndex) return null;

        if (email.indexOf('@', atIndex + 1) != -1) return null;

        String localPart = email.substring(0, atIndex).trim().toLowerCase();

        // FIX: telefon + mail sklejone: "0176...567mona@..." -> utnij cyfry przed literą
        localPart = localPart.replaceFirst("^\\d{3,}(?=[a-z])", "");
        if (localPart.isBlank()) return null;

        if (!isLocalPartAllowed(localPart)) return null;

        String hostWithoutTld = email.substring(atIndex + 1, lastDot).trim();
        String tldPart = email.substring(lastDot + 1).trim();

        String tld = extractKnownTld(tldPart);
        if (tld == null) return null;

        String domain = hostWithoutTld.toLowerCase() + "." + tld;

        if (mxCheckEnabled) {
            MxStatus mx = domainMxStatus(domain);
            if (mx == MxStatus.INVALID) return null;

            if (mx == MxStatus.UNKNOWN) {
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

    // =========================
    // MX check (robust)
    // =========================

    private MxStatus domainMxStatus(String domain) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");

            // timeouts
            env.put("com.sun.jndi.dns.timeout.initial", String.valueOf(mxTimeoutMs));
            env.put("com.sun.jndi.dns.timeout.retries", "1");

            DirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(domain, new String[]{"MX"});
            Attribute attr = attrs.get("MX");

            return (attr != null && attr.size() > 0) ? MxStatus.VALID : MxStatus.INVALID;
        } catch (NamingException e) {
            return MxStatus.UNKNOWN;
        }
    }
}
