package com.mike.leadfarmfinder.service.emailextractor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class RegexTextExtractor implements EmailSourceExtractor {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    @Override
    public Stream<String> extractCandidates(String html) {
        if (html == null || html.isBlank()) return Stream.empty();

        Matcher matcher = EMAIL_PATTERN.matcher(html);

        Stream.Builder<String> builder = Stream.builder();
        while (matcher.find()) {
            builder.add(matcher.group());
        }
        return builder.build();
    }
}
