package com.mike.leadfarmfinder.service.emailextractor;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class TextObfuscationNormalizer {

    private static final Pattern AT_BRACKETS =
            Pattern.compile("(?i)\\s*[\\(\\[\\{<]\\s*at\\s*[\\)\\]\\}>]\\s*");
    private static final Pattern DOT_BRACKETS =
            Pattern.compile("(?i)\\s*[\\(\\[\\{<]\\s*dot\\s*[\\)\\]\\}>]\\s*");
    private static final Pattern AT_SPACES =
            Pattern.compile("(?i)\\s+at\\s+");
    private static final Pattern DOT_SPACES =
            Pattern.compile("(?i)\\s+dot\\s+");

    public String normalize(String input) {
        if (input == null || input.isBlank()) return input;
        String s = input;
        s = AT_BRACKETS.matcher(s).replaceAll("@");
        s = DOT_BRACKETS.matcher(s).replaceAll(".");
        s = AT_SPACES.matcher(s).replaceAll("@");
        s = DOT_SPACES.matcher(s).replaceAll(".");
        return s;
    }

}
