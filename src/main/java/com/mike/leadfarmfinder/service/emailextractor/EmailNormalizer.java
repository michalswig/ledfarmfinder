package com.mike.leadfarmfinder.service.emailextractor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Component
@Slf4j
public class EmailNormalizer {

    private static final Pattern TRAILING_JUNK =
            Pattern.compile("[\\)\\]\\}\\.,;:'\"<>]+$");

    private static final Pattern LEADING_PHONE_DIGITS_BEFORE_LETTER =
            Pattern.compile("^\\d{2,}(?=[a-z])");

    private static final Pattern LEADING_JUNK =
            Pattern.compile("^[\"'<>()\\[\\];:,]+");

    private static final Pattern LEADING_PERCENT_ENCODING =
            Pattern.compile("^%[0-9A-Fa-f]{2}");

    private static final int PERCENT_ENCODING_PREFIX_LEN = 3;

    public String normalizeRawCandidate(String raw) {
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

        email = LEADING_JUNK.matcher(email).replaceAll("").trim();
        email = TRAILING_JUNK.matcher(email).replaceAll("").trim();
        if (email.isEmpty()) return null;

        while (LEADING_PERCENT_ENCODING.matcher(email).lookingAt()) {
            email = email.substring(PERCENT_ENCODING_PREFIX_LEN).trim();
        }
        if (email.isEmpty()) return null;

        char first = email.charAt(0);
        if (!Character.isLetterOrDigit(first)) return null;

        return email;
    }

    public String normalizeLocalPart(String localPart) {
        if (localPart == null) return null;

        String lp = localPart.trim().toLowerCase();
        lp = LEADING_PHONE_DIGITS_BEFORE_LETTER.matcher(lp).replaceFirst("");
        return lp.isBlank() ? null : lp;
    }
}