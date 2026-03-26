package com.mike.leadfarmfinder.service.discovery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscoveryUrlNormalizerTest {

    private final DiscoveryUrlNormalizer normalizer = new DiscoveryUrlNormalizer();

    static Stream<org.junit.jupiter.params.provider.Arguments> shouldNormalizeUrls() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("HTTPS://WWW.Example.com/", "https://example.com/"),
                org.junit.jupiter.params.provider.Arguments.of("https://example.com/index.html", "https://example.com/"),
                org.junit.jupiter.params.provider.Arguments.of("https://example.com/index.htm", "https://example.com/"),
                org.junit.jupiter.params.provider.Arguments.of("https://example.com", "https://example.com/"),
                org.junit.jupiter.params.provider.Arguments.of("http://example.com/", "http://example.com/"),
                org.junit.jupiter.params.provider.Arguments.of("     https://example.com/      ", "https://example.com/"),
                org.junit.jupiter.params.provider.Arguments.of("https://example.com/blog", "https://example.com/blog"),
                org.junit.jupiter.params.provider.Arguments.of("https://example.com/blog/", "https://example.com/blog"),
                org.junit.jupiter.params.provider.Arguments.of("https://www.example.com/blog/", "https://example.com/blog"),
                org.junit.jupiter.params.provider.Arguments.of("https://www.example.com/blog/index.html", "https://example.com/blog"),
                org.junit.jupiter.params.provider.Arguments.of("https://www.example.com/blog/index.htm", "https://example.com/blog"),
                org.junit.jupiter.params.provider.Arguments.of("HTTPS://WWW.EXAMPLE.COM/BLOG/", "https://example.com/BLOG")
        );
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> shouldReturnTrimmedInputWhenUrlCannotBeNormalized() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("not a valid url", "not a valid url"),
                org.junit.jupiter.params.provider.Arguments.of("   not a valid url   ", "not a valid url"),
                org.junit.jupiter.params.provider.Arguments.of("example.com", "example.com"),
                org.junit.jupiter.params.provider.Arguments.of("www.example.com/test", "www.example.com/test")
        );
    }

    @ParameterizedTest(name = "[{index}] input=''{0}'' -> expected=''{1}''")
    @MethodSource("shouldNormalizeUrls")
    @DisplayName("should normalize valid urls")
    void shouldNormalizeValidUrls(String input, String expected) {
        assertEquals(expected, normalizer.normalizeUrl(input));
    }

    @ParameterizedTest(name = "[{index}] input=''{0}'' -> expected fallback=''{1}''")
    @MethodSource("shouldReturnTrimmedInputWhenUrlCannotBeNormalized")
    @DisplayName("should return trimmed input when uri cannot be parsed or host is missing")
    void shouldReturnTrimmedInputForInvalidOrUnsupportedUrls(String input, String expected) {
        assertEquals(expected, normalizer.normalizeUrl(input));
    }

    @Test
    @DisplayName("should keep root slash for domain root")
    void shouldKeepRootSlashForDomainRoot() {
        String result = normalizer.normalizeUrl("https://www.example.com/");
        assertEquals("https://example.com/", result);
    }

    @Test
    @DisplayName("should remove trailing slash only for non-root path")
    void shouldRemoveTrailingSlashOnlyForNonRootPath() {
        String result = normalizer.normalizeUrl("https://example.com/contact/");
        assertEquals("https://example.com/contact", result);
    }

    @Test
    @DisplayName("should remove index file and convert empty path to root slash")
    void shouldConvertIndexFileToRootSlash() {
        String result = normalizer.normalizeUrl("https://example.com/index.html");
        assertEquals("https://example.com/", result);
    }

    @Test
    @DisplayName("should drop query and fragment during normalization")
    void shouldDropQueryAndFragment() {
        String result = normalizer.normalizeUrl("https://www.example.com/blog/?x=1#section");
        assertEquals("https://example.com/blog", result);
    }
}