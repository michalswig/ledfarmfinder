package com.mike.leadfarmfinder.service.emailextractor;

import java.util.stream.Stream;

public interface EmailSourceExtractor {
    Stream<String> extractCandidates(String html);
}
