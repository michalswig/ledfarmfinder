package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.config.EmailExtractorProperties;
import com.mike.leadfarmfinder.service.emailextractor.EmailNormalizer;
import com.mike.leadfarmfinder.service.emailextractor.EmailSourceExtractor;
import com.mike.leadfarmfinder.service.emailextractor.EmailValidator;
import com.mike.leadfarmfinder.service.emailextractor.TextObfuscationNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
    private final EmailValidator emailValidator;

    public Set<String> extractEmails(String html) {

        if (html == null || html.isBlank()) return Set.of();

        String normalizedHtml = obfuscationNormalizer.normalize(html);

        return sources.stream()
                .flatMap(s -> s.extractCandidates(normalizedHtml))
                .map(this::toValidEmailOrNull)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    String toValidEmailOrNull(String raw) {

        String email = emailNormalizer.normalizeRawCandidate(raw);
        if (email == null) return null;

        int atIndex = email.indexOf('@');
        int lastDot = email.lastIndexOf('.');
        if (atIndex <= 0 || lastDot <= atIndex) return null;

        if (email.indexOf('@', atIndex + 1) != -1) return null;

        String localPart = emailNormalizer.normalizeLocalPart(email.substring(0, atIndex));
        if (localPart == null) return null;

        String hostWithoutTld = email.substring(atIndex + 1, lastDot).trim();
        String tldPart = email.substring(lastDot + 1).trim();

        if (!emailValidator.isLocalPartAllowed(localPart)) return null;

        if (!emailValidator.isHostWithoutTldAllowed(hostWithoutTld)) return null;

        String tld = emailValidator.extractKnownTld(tldPart);
        if (tld == null) return null;

        String domain = hostWithoutTld.toLowerCase() + "." + tld;

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

}