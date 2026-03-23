package com.mike.leadfarmfinder.service.discovery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

@Component
@Slf4j
public class DiscoveryUrlNormalizer {

    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            ".pdf",
            ".jpg", ".jpeg", ".png", ".gif", ".webp",
            ".zip", ".rar", ".7z",
            ".doc", ".docx",
            ".xls", ".xlsx", ".ods",
            ".ppt", ".pptx",
            ".csv", ".txt", ".xml"
    );

    public String normalizeUrl(String url) {
        try {
            URI uri = new URI(url.trim());

            String scheme = (uri.getScheme() == null) ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);

            String host = uri.getHost();
            if (host == null) return url.trim();

            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) host = host.substring(4);

            String path = uri.getPath();
            if (path == null || path.isBlank()) path = "/";

            String p = path.toLowerCase(Locale.ROOT);
            if (p.endsWith("/index.html") || p.endsWith("/index.htm")) {
                path = path.substring(0, path.lastIndexOf("/index."));
                if (path.isBlank()) path = "/";
            }

            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            return new URI(scheme, host, path, null).toString();

        } catch (URISyntaxException e) {
            return url.trim();
        }
    }

    public String extractNormalizedDomain(String url) {
        String domain = extractDomain(url);
        if (domain == null) return null;
        return domain.toLowerCase(Locale.ROOT).trim();
    }

    public boolean isNotFileUrl(String url) {
        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            if (path == null) return true;

            String p = path.toLowerCase(Locale.ROOT);
            for (String ext : BLOCKED_EXTENSIONS) {
                if (p.endsWith(ext)) {
                    log.info("DiscoveryService: dropping url={} (blocked file extension={})", url, ext);
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.info("DiscoveryService: dropping url={} (invalid url for file-check: {})", url, e.getMessage());
            return false;
        }
    }

    private String extractDomain(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null) return null;

            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) host = host.substring(4);
            return host;
        } catch (Exception e) {
            log.warn("DiscoveryService: failed to extract domain from {}: {}", url, e.getMessage());
            return null;
        }
    }


}
