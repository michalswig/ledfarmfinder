package com.mike.leadfarmfinder.service.emailextractor;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RegexTextExtractor implements EmailSourceExtractor {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    @Override
    public List<String> extractCandidates(String html) {
        if (html == null || html.isBlank()) return List.of();

        Matcher matcher = EMAIL_PATTERN.matcher(html);

        List<String> candidates = new ArrayList<>();
        while (matcher.find()) {
            candidates.add(matcher.group());
        }
        return candidates;
    }
}
