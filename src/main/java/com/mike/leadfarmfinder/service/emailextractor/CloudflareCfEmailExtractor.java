package com.mike.leadfarmfinder.service.emailextractor;

import com.mike.leadfarmfinder.service.CloudflareEmailDecoder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class CloudflareCfEmailExtractor implements EmailSourceExtractor {

    private static final Pattern CLOUDFLARE_PATTERN =
            Pattern.compile("data-cfemail=\"([0-9a-fA-F]+)\"");

    @Override
    public Stream<String> extractCandidates(String html) {
        if (html == null || html.isBlank()) return Stream.empty();
        Matcher matcher = CLOUDFLARE_PATTERN.matcher(html);
        Stream.Builder<String> builder = Stream.builder();
        while (matcher.find()) {
            String decoded = CloudflareEmailDecoder.decode(matcher.group(1));
            if (decoded != null && !decoded.isBlank()) builder.add(decoded);
        }
        return builder.build();
    }
}
