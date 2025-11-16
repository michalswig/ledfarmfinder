package com.mike.leadfarmfinder.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class EmailExtractor {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    private static final Pattern CLOUDFLARE_PATTERN =
            Pattern.compile("data-cfemail=\"([0-9a-fA-F]+)\"");

    // Extend this list as needed
    private static final Set<String> ALLOWED_TLDS = Set.of(
            "de", "com"
    );

    public Set<String> extractEmails(String html) {
        Set<String> results = new LinkedHashSet<>();

        // 1) Cloudflare data-cfemail
        Matcher cf = CLOUDFLARE_PATTERN.matcher(html);
        while (cf.find()) {
            String encoded = cf.group(1);
            String decoded = CloudflareEmailDecoder.decode(encoded);

            if (decoded == null) {
                continue;
            }

            String normalized = normalizeEmail(decoded);
            if (normalized != null) {
                results.add(normalized);
            }
        }

        // 2) zwykłe maile w HTML
        Matcher matcher = EMAIL_PATTERN.matcher(html);
        while (matcher.find()) {
            String raw = matcher.group();
            String normalized = normalizeEmail(raw);
            if (normalized != null) {
                results.add(normalized);
            }
        }

        return results;
    }

    /**
     * Normalize email:
     * - trim
     * - lowercase
     * - collapse weird endings like ".deust" -> ".de" if "de" is an allowed TLD
     * - return null if TLD is not allowed
     */
    private String normalizeEmail(String raw) {
        if (raw == null) {
            return null;
        }

        String email = raw.trim();
        if (email.isEmpty()) {
            return null;
        }

        int atIndex = email.indexOf('@');
        int lastDot = email.lastIndexOf('.');
        if (atIndex < 0 || lastDot < 0 || lastDot < atIndex) {
            return null;
        }

        String head = email.substring(0, lastDot);        // local + domain part
        String tldPart = email.substring(lastDot + 1);    // everything after the last dot

        String tld = extractKnownTld(tldPart);
        if (tld == null) {
            return null;
        }

        // normalize to lowercase
        return (head + "." + tld).toLowerCase();
    }

    /**
     * From something like "de", "deust", "DE", "DeUST" etc.
     * return a known TLD if it starts with it (e.g. "deust" -> "de").
     */
    private String extractKnownTld(String tldPart) {
        if (tldPart == null || tldPart.isEmpty()) {
            return null;
        }

        String lower = tldPart.toLowerCase();

        for (String allowed : ALLOWED_TLDS) {
            if (lower.equals(allowed) || lower.startsWith(allowed)) {
                return allowed;
            }
        }

        return null;
    }
}
