package com.mike.leadfarmfinder.service.emailextractor;

import com.mike.leadfarmfinder.service.CloudflareEmailDecoder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CloudflareCfEmailExtractor implements EmailSourceExtractor {

    private static final Pattern CLOUDFLARE_PATTERN =
            Pattern.compile("data-cfemail=\"([0-9a-fA-F]+)\"");

    @Override
    public List<String> extractCandidates(String html) {
        if (html == null || html.isBlank()) return List.of();
        Matcher matcher = CLOUDFLARE_PATTERN.matcher(html);
        List<String> candidates = new ArrayList<>();

        while (matcher.find()) {
            String decoded = CloudflareEmailDecoder.decode(matcher.group(1));
            if (decoded != null && !decoded.isBlank()) candidates.add(decoded);
        }

        return candidates;
    }
}
