package com.mike.leadfarmfinder.service.discovery;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

@Component
public class DiscoveryContentTypeChecker {

    private static final String USER_AGENT = "Mozilla/5.0 (compatible; LeadFarmFinderBot/1.0)";
    private static final String ACCEPT_HEADER = "text/html,application/xhtml+xml;q=0.9,*/*;q=0.1";

    private static final Duration PRECHECK_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    private final HttpClient httpClient;

    public DiscoveryContentTypeChecker() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public DiscoveryContentTypeResult check(String url) {
        Optional<URI> uri = parseUri(url);
        if (uri.isEmpty()) {
            return DiscoveryContentTypeResult.unknown();
        }

        Optional<DiscoveryContentTypeResult> headResult = tryHead(uri.get());
        if (headResult.isPresent()) {
            DiscoveryContentTypeResult result = headResult.get();
            if (result.reason() == DiscoveryContentTypeResult.Reason.OK || result.shouldSkipFullFetch()) {
                return result;
            }
        }

        Optional<DiscoveryContentTypeResult> getResult = tryGetHeadersOnly(uri.get());
        if (getResult.isPresent()) {
            DiscoveryContentTypeResult result = getResult.get();
            if (result.reason() == DiscoveryContentTypeResult.Reason.OK || result.shouldSkipFullFetch()) {
                return result;
            }
        }

        return headResult.orElseGet(DiscoveryContentTypeResult::unknown);
    }

    private Optional<URI> parseUri(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(URI.create(url.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Optional<DiscoveryContentTypeResult> tryHead(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(PRECHECK_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", ACCEPT_HEADER)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();

        return sendProbe(request);
    }

    private Optional<DiscoveryContentTypeResult> tryGetHeadersOnly(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(PRECHECK_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", ACCEPT_HEADER)
                .GET()
                .build();

        return sendProbe(request);
    }

    private Optional<DiscoveryContentTypeResult> sendProbe(HttpRequest request) {
        try {
            HttpResponse<Void> response = httpClient.send(request, BodyHandlers.discarding());
            int statusCode = response.statusCode();

            if (statusCode < 200 || statusCode >= 300) {
                return Optional.empty();
            }

            String rawContentType = response.headers()
                    .firstValue("Content-Type")
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .orElse(null);

            return Optional.of(classifyContentType(rawContentType));
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    static DiscoveryContentTypeResult classifyContentType(String rawContentTypeHeader) {
        if (rawContentTypeHeader == null || rawContentTypeHeader.isBlank()) {
            return DiscoveryContentTypeResult.unknown();
        }

        String primaryMimeType = primaryMimeType(rawContentTypeHeader);

        if ("application/pdf".equals(primaryMimeType)) {
            return DiscoveryContentTypeResult.pdf(rawContentTypeHeader);
        }

        if ("text/html".equals(primaryMimeType) || "application/xhtml+xml".equals(primaryMimeType)) {
            return DiscoveryContentTypeResult.ok(rawContentTypeHeader);
        }

        return DiscoveryContentTypeResult.nonHtml(rawContentTypeHeader);
    }

    static String primaryMimeType(String contentTypeHeader) {
        if (contentTypeHeader == null || contentTypeHeader.isBlank()) {
            return "";
        }

        return contentTypeHeader
                .split(";", 2)[0]
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}