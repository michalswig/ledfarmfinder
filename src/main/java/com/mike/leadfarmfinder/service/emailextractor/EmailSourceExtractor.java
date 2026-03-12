package com.mike.leadfarmfinder.service.emailextractor;

import java.util.List;

public interface EmailSourceExtractor {
    List<String> extractCandidates(String html);
}
