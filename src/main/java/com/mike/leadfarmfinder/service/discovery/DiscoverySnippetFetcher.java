package com.mike.leadfarmfinder.service.discovery;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DiscoverySnippetFetcher {

    public String fetchTextSnippet(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; LeadFarmFinderBot/1.0)")
                    .timeout(10_000)
                    .followRedirects(true)
                    .get();

            doc.select("script,style,noscript").remove();

            String text = doc.text();
            if (text == null) {
                return "";
            }

            text = text.trim();

            if (text.length() < 120) {
                return "";
            }

            int maxLen = 2000;
            return text.length() > maxLen ? text.substring(0, maxLen) : text;

        } catch (UnsupportedMimeTypeException e) {
            log.warn("DiscoverySnippetFetcher: failed to fetch text from {}: unsupported mime {}", url, e.getMimeType());
            return "";
        } catch (Exception e) {
            log.warn("DiscoverySnippetFetcher: failed to fetch text from {}: {}", url, e.getMessage());
            return "";
        }
    }
}