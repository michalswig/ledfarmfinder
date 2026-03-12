package com.mike.leadfarmfinder.service.emailextractor;

import com.mike.leadfarmfinder.config.EmailExtractorProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class EmailValidator {

    private final EmailExtractorProperties props;

    private static final Pattern LOCAL_PART_PATTERN =
            Pattern.compile("^[a-z0-9._%+-]{2,40}$");

    private static final Pattern SUSPICIOUS_UNICODE_ESCAPE =
            Pattern.compile("u00[0-9a-fA-F]{2}");

    private static final Pattern HOST_PATTERN = Pattern.compile("^[a-zA-Z0-9.-]+$");

    public boolean isLocalPartAllowed(String localPart) {
        if (localPart == null) return false;
        String lp = localPart.trim().toLowerCase();
        if (SUSPICIOUS_UNICODE_ESCAPE.matcher(lp).find()) return false;
        return LOCAL_PART_PATTERN.matcher(lp).matches();
    }

    public boolean isHostWithoutTldAllowed(String hostWithoutTldRaw) {
        if (hostWithoutTldRaw == null) return false;

        String h = hostWithoutTldRaw.trim();
        if (h.isBlank()) return false;
        if (!HOST_PATTERN.matcher(h).matches()) return false;
        if (h.startsWith(".") || h.endsWith(".")) return false;
        if (h.startsWith("-") || h.endsWith("-")) return false;
        if (h.contains("..")) return false;

        return true;
    }

    public String extractKnownTld(String tldPart) {
        if (tldPart == null || tldPart.isEmpty()) return null;

        String lower = tldPart.toLowerCase();

        for (String allowed : props.knownTlds()) {
            if (lower.equals(allowed) || lower.startsWith(allowed)) {
                return allowed;
            }
        }
        return null;
    }

}