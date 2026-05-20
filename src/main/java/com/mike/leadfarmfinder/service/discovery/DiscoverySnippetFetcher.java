package com.mike.leadfarmfinder.service.discovery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DiscoverySnippetFetcher {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/129.0.0.0 Safari/537.36";

    private static final String REFERRER = "https://www.google.com";

    private static final int FETCH_TIMEOUT_MILLIS = 12_000;
    private static final int MIN_TEXT_LENGTH = 120;
    private static final int MAX_TEXT_LENGTH = 2_000;
    private static final int MAX_ATTEMPTS_PER_URL = 2;

    private final DiscoveryContentTypeChecker contentTypeChecker;

    private record FetchResult(String snippet, boolean hostBlocked) {
        static FetchResult blocked() { return new FetchResult("", true); }
        static FetchResult empty()   { return new FetchResult("", false); }
        static FetchResult of(String s) { return new FetchResult(s, false); }
    }

    public String fetchTextSnippet(String url) {
        Set<String> candidates = buildCandidateUrls(url);
        String blockedHost = null;

        for (String candidateUrl : candidates) {

            if (blockedHost != null && blockedHost.equals(extractHost(candidateUrl))) {
                log.debug("DiscoverySnippetFetcher: SKIP (host blocked) url={}", candidateUrl);
                continue;
            }

            FetchResult result = fetchSingleCandidate(candidateUrl);

            if (result.hostBlocked()) {
                blockedHost = extractHost(candidateUrl);
                log.info("DiscoverySnippetFetcher: host blocked, skipping remaining candidates host={}", blockedHost);
                continue;
            }

            if (!result.snippet().isBlank()) {
                if (!candidateUrl.equals(url)) {
                    log.info("DiscoverySnippetFetcher: fallback success originalUrl={} fallbackUrl={}", url, candidateUrl);
                }
                return result.snippet();
            }
        }

        return "";
    }

    private FetchResult fetchSingleCandidate(String url) {
        DiscoveryContentTypeResult typeResult = contentTypeChecker.check(url);

        if (typeResult != null && typeResult.shouldSkipFullFetch()) {
            log.info(
                    "DiscoverySnippetFetcher: SKIP (reason={} content-type={}) url={}",
                    typeResult.reason(),
                    typeResult.contentTypeOr("missing"),
                    url
            );
            return FetchResult.empty();
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS_PER_URL; attempt++) {
            try {
                Document document = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .referrer(REFERRER)
                        .timeout(FETCH_TIMEOUT_MILLIS)
                        .followRedirects(true)
                        .ignoreHttpErrors(false)
                        .get();

                document.select("script,style,noscript").remove();

                String text = document.text();
                if (text == null) {
                    return FetchResult.empty();
                }

                String trimmedText = text.trim();
                if (trimmedText.length() < MIN_TEXT_LENGTH) {
                    return FetchResult.empty();
                }

                String snippet = trimmedText.length() > MAX_TEXT_LENGTH
                        ? trimmedText.substring(0, MAX_TEXT_LENGTH)
                        : trimmedText;

                return FetchResult.of(snippet);

            } catch (UnsupportedMimeTypeException e) {
                log.warn(
                        "DiscoverySnippetFetcher: failed to fetch text from {}: unsupported mime {}",
                        url,
                        e.getMimeType()
                );
                return FetchResult.empty();

            } catch (HttpStatusException e) {
                log.warn(
                        "DiscoverySnippetFetcher: failed to fetch text from {}: HTTP {}",
                        url,
                        e.getStatusCode()
                );
                if (e.getStatusCode() == 403) {
                    return FetchResult.blocked();
                }
                if (e.getStatusCode() == 404) {
                    return FetchResult.empty();
                }

            } catch (Exception e) {
                log.warn(
                        "DiscoverySnippetFetcher: failed to fetch text from {} (attempt {}/{}): {}",
                        url,
                        attempt,
                        MAX_ATTEMPTS_PER_URL,
                        e.getMessage()
                );

                if (isHostLevelError(e)) {
                    return FetchResult.blocked();
                }
            }
        }

        return FetchResult.empty();
    }

    private boolean isHostLevelError(Exception e) {
        String chainMessages = extractChainMessages(e);
        return chainMessages.contains("PKIX path")
                || chainMessages.contains("certificate")
                || chainMessages.contains("SSL")
                || chainMessages.contains("handshake")
                || chainMessages.contains("Connection refused")
                || chainMessages.contains("Connection reset")
                || chainMessages.contains("UnknownHostException")
                || chainMessages.contains("No route to host");
    }

    private String extractChainMessages(Exception e) {
        StringBuilder sb = new StringBuilder();
        Throwable current = e;
        while (current != null) {
            if (current.getMessage() != null) {
                sb.append(current.getMessage()).append(' ');
            }
            sb.append(current.getClass().getSimpleName()).append(' ');
            current = current.getCause();
        }
        return sb.toString();
    }

    private String extractHost(String url) {
        try {
            String host = new URI(url).getHost();
            return host != null ? host : url;
        } catch (Exception e) {
            return url;
        }
    }

    private Set<String> buildCandidateUrls(String originalUrl) {
        Set<String> urls = new LinkedHashSet<>();
        urls.add(originalUrl);

        String root = rootUrl(originalUrl);
        if (root == null) {
            return urls;
        }

        urls.add(root);
        urls.add(join(root, "/impressum"));
        urls.add(join(root, "/impressum/"));
        urls.add(join(root, "/kontakt"));
        urls.add(join(root, "/kontakt/"));
        urls.add(join(root, "/contact"));
        urls.add(join(root, "/contact/"));

        return urls;
    }

    private String rootUrl(String url) {
        try {
            URI uri = new URI(url);
            String scheme = (uri.getScheme() == null) ? "https" : uri.getScheme();
            String host = uri.getHost();
            if (host == null) return null;
            return scheme + "://" + host;
        } catch (Exception e) {
            return null;
        }
    }

    private String join(String root, String path) {
        if (root.endsWith("/") && path.startsWith("/")) {
            return root.substring(0, root.length() - 1) + path;
        }
        if (!root.endsWith("/") && !path.startsWith("/")) {
            return root + "/" + path;
        }
        return root + path;
    }
}