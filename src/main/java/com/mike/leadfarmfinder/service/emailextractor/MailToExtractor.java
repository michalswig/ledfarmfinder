package com.mike.leadfarmfinder.service.emailextractor;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class MailToExtractor implements EmailSourceExtractor {

    private final TextObfuscationNormalizer obfuscationNormalizer;

    private static final Pattern MAILTO_PATTERN =
            Pattern.compile("(?i)mailto:([^\"'\\s>]+)");

    @Override
    public Stream<String> extractCandidates(String html) {
        if (html == null || html.isBlank()) return Stream.empty();

        Matcher matcher = MAILTO_PATTERN.matcher(html);

        Stream.Builder<String> builder = Stream.builder();
        while (matcher.find()) {
            String raw = matcher.group(1);
            int q = raw.indexOf('?');
            if (q >= 0) raw = raw.substring(0, q);
            raw = obfuscationNormalizer.normalize(raw);
            if (!raw.isBlank()) builder.add(raw);
        }
        return builder.build();
    }
}
