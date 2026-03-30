package com.mike.leadfarmfinder.service.discovery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DiscoverySnippetFetcher {

    private static final String USER_AGENT = "Mozilla/5.0 (compatible; LeadFarmFinderBot/1.0)";
    private static final int FETCH_TIMEOUT_MILLIS = 10_000;
    private static final int MIN_TEXT_LENGTH = 120;
    private static final int MAX_TEXT_LENGTH = 2_000;

    private final DiscoveryContentTypeChecker contentTypeChecker;

    public String fetchTextSnippet(String url) {
        DiscoveryContentTypeResult typeResult = contentTypeChecker.check(url);

        if (typeResult.shouldSkipFullFetch()) {
            log.info(
                    "DiscoverySnippetFetcher: SKIP (reason={} content-type={}) url={}",
                    typeResult.reason(),
                    typeResult.contentTypeOr("missing"),
                    url
            );
            return "";
        }

        try {
            Document document = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(FETCH_TIMEOUT_MILLIS)
                    .followRedirects(true)
                    .get();

            document.select("script,style,noscript").remove();

            String text = document.text();
            if (text == null) {
                return "";
            }

            String trimmedText = text.trim();
            if (trimmedText.length() < MIN_TEXT_LENGTH) {
                return "";
            }

            return trimmedText.length() > MAX_TEXT_LENGTH
                    ? trimmedText.substring(0, MAX_TEXT_LENGTH)
                    : trimmedText;
        } catch (UnsupportedMimeTypeException e) {
            log.warn(
                    "DiscoverySnippetFetcher: failed to fetch text from {}: unsupported mime {}",
                    url,
                    e.getMimeType()
            );
            return "";
        } catch (Exception e) {
            log.warn(
                    "DiscoverySnippetFetcher: failed to fetch text from {}: {}",
                    url,
                    e.getMessage()
            );
            return "";
        }
    }
}