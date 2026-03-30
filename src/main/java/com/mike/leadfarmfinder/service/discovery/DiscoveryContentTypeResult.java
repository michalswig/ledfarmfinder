package com.mike.leadfarmfinder.service.discovery;

import java.util.Optional;

public record DiscoveryContentTypeResult(
        boolean isHtml,
        Reason reason,
        Optional<String> contentType
) {

    public enum Reason {
        OK,
        PDF,
        NON_HTML,
        UNKNOWN
    }

    public boolean shouldSkipFullFetch() {
        return reason == Reason.PDF || reason == Reason.NON_HTML;
    }

    public String contentTypeOr(String fallback) {
        return contentType.orElse(fallback);
    }

    public static DiscoveryContentTypeResult ok(String contentType) {
        return new DiscoveryContentTypeResult(true, Reason.OK, Optional.ofNullable(contentType));
    }

    public static DiscoveryContentTypeResult pdf(String contentType) {
        return new DiscoveryContentTypeResult(false, Reason.PDF, Optional.ofNullable(contentType));
    }

    public static DiscoveryContentTypeResult nonHtml(String contentType) {
        return new DiscoveryContentTypeResult(false, Reason.NON_HTML, Optional.ofNullable(contentType));
    }

    public static DiscoveryContentTypeResult unknown() {
        return new DiscoveryContentTypeResult(false, Reason.UNKNOWN, Optional.empty());
    }

    public static DiscoveryContentTypeResult unknown(String contentType) {
        return new DiscoveryContentTypeResult(false, Reason.UNKNOWN, Optional.ofNullable(contentType));
    }
}