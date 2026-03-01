package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.config.EmailExtractorProperties;
import com.mike.leadfarmfinder.service.emailextractor.EmailNormalizer;
import com.mike.leadfarmfinder.service.emailextractor.EmailSourceExtractor;
import com.mike.leadfarmfinder.service.emailextractor.TextObfuscationNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailExtractor {

    private final MxLookUp mxLookUp;
    private final EmailExtractorProperties props;
    private final TextObfuscationNormalizer obfuscationNormalizer;
    private final List<EmailSourceExtractor> sources;
    private final EmailNormalizer emailNormalizer;

    /**
     * Email local-part (before '@') validation:
     * - ASCII only
     * - length 2–40
     * - allowed chars: a-z, 0-9, dot, underscore, percent, plus, minus
     * <p>
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

    public Set<String> extractEmails(String html) {

        if (html == null || html.isBlank()) return new LinkedHashSet<>();

        String normalizedHtml = obfuscationNormalizer.normalize(html);

        return sources.stream()
                .flatMap(s -> s.extractCandidates(normalizedHtml))
                .map(this::normalizeEmail)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    String normalizeEmail(String raw) {

        String email = emailNormalizer.normalizeRawCandidate(raw);
        if (email == null) return null;

        int atIndex = email.indexOf('@');
        int lastDot = email.lastIndexOf('.');
        if (atIndex <= 0 || lastDot <= atIndex) return null;

        // Second '@' -> reject
        if (email.indexOf('@', atIndex + 1) != -1) return null;

        // --- split ---
        String localPart = emailNormalizer.normalizeLocalPart(email.substring(0, atIndex));
        if (localPart == null) return null;

        String hostWithoutTld = email.substring(atIndex + 1, lastDot).trim();
        String tldPart = email.substring(lastDot + 1).trim();

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
                switch (props.mxUnknownPolicy()) {
                    case DROP -> {
                        return null;
                    }
                    case WARN -> log.warn("MX check UNKNOWN for domain '{}', email {}", domain, raw);
                    case ALLOW -> { /* nothing */}
                }
            }
        }

        return localPart + "@" + domain;
    }

    private String extractKnownTld(String tldPart) {
        if (tldPart == null || tldPart.isEmpty()) return null;

        String lower = tldPart.toLowerCase();
        for (String allowed : props.knownTlds()) {
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