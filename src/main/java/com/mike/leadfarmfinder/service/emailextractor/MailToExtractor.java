package com.mike.leadfarmfinder.service.emailextractor;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class MailToExtractor implements EmailSourceExtractor {

    private final TextObfuscationNormalizer obfuscationNormalizer;

    private static final Pattern MAILTO_PATTERN =
            Pattern.compile("(?i)mailto:([^\"'\\s>]+)");

    @Override
    public List<String> extractCandidates(String html) {
        if (html == null || html.isBlank()) return List.of();

        Matcher matcher = MAILTO_PATTERN.matcher(html);

        List<String> candidates = new ArrayList<>();
        while (matcher.find()) {
            String raw = matcher.group(1);
            int q = raw.indexOf('?');
            if (q >= 0) raw = raw.substring(0, q);
            raw = obfuscationNormalizer.normalize(raw);
            if (!raw.isBlank()) candidates.add(raw);
        }
        return candidates;
    }
}
