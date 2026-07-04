package com.mike.leadfarmfinder.service.discovery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.IDN;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DiscoveryUrlNormalizerTest {

    private final DiscoveryUrlNormalizer normalizer = new DiscoveryUrlNormalizer();

    // -------------------------------------------------------------------------
    // Happy path — normalne URL-e ASCII
    // -------------------------------------------------------------------------

    static Stream<Arguments> shouldNormalizeUrls() {
        return Stream.of(
                Arguments.of("HTTPS://WWW.Example.com/",                    "https://example.com/"),
                Arguments.of("https://example.com/index.html",              "https://example.com/"),
                Arguments.of("https://example.com/index.htm",               "https://example.com/"),
                Arguments.of("https://example.com",                         "https://example.com/"),
                Arguments.of("http://example.com/",                         "http://example.com/"),
                Arguments.of("     https://example.com/      ",             "https://example.com/"),
                Arguments.of("https://example.com/blog",                    "https://example.com/blog"),
                Arguments.of("https://example.com/blog/",                   "https://example.com/blog"),
                Arguments.of("https://www.example.com/blog/",               "https://example.com/blog"),
                Arguments.of("https://www.example.com/blog/index.html",     "https://example.com/blog"),
                Arguments.of("https://www.example.com/blog/index.htm",      "https://example.com/blog"),
                Arguments.of("HTTPS://WWW.EXAMPLE.COM/BLOG/",               "https://example.com/BLOG")
        );
    }

    @ParameterizedTest(name = "[{index}] ''{0}'' -> ''{1}''")
    @MethodSource("shouldNormalizeUrls")
    @DisplayName("should normalize valid ASCII urls")
    void shouldNormalizeValidUrls(String input, String expected) {
        assertEquals(expected, normalizer.normalizeUrl(input));
    }

    // -------------------------------------------------------------------------
    // IDN — umlauty i inne non-ASCII hostname
    // -------------------------------------------------------------------------

    static Stream<Arguments> shouldNormalizeIdnUrls() {
        return Stream.of(
                Arguments.of(
                        "https://www.gemüsehof-schaper.de/kontakt",
                        "https://" + IDN.toASCII("gemüsehof-schaper.de") + "/kontakt"
                ),
                Arguments.of(
                        "https://gärtnerei-eichfelder.de",
                        "https://" + IDN.toASCII("gärtnerei-eichfelder.de") + "/"
                ),
                Arguments.of(
                        "https://www.hühnerhof-bührke.de/hofladen/",
                        "https://" + IDN.toASCII("hühnerhof-bührke.de") + "/hofladen"
                ),
                Arguments.of(
                        "https://www.geflügelhof-zoller.de/",
                        "https://" + IDN.toASCII("geflügelhof-zoller.de") + "/"
                ),
                Arguments.of(
                        "http://www.naturlandhof-kühnert.de",
                        "http://" + IDN.toASCII("naturlandhof-kühnert.de") + "/"
                )
        );
    }

    @ParameterizedTest(name = "[{index}] IDN ''{0}''")
    @MethodSource("shouldNormalizeIdnUrls")
    @DisplayName("should normalize urls with umlaut hostnames (IDN → punycode)")
    void shouldNormalizeIdnHostnames(String input, String expected) {
        assertEquals(expected, normalizer.normalizeUrl(input));
    }

    // -------------------------------------------------------------------------
    // Nieparsowalne URL-e → null (nowy kontrakt)
    // -------------------------------------------------------------------------

    static Stream<String> shouldReturnNullForUnparseable() {
        return Stream.of(
                "not a valid url",
                "   not a valid url   ",
                "example.com",
                "www.example.com/test"
        );
    }

    @ParameterizedTest(name = "[{index}] ''{0}'' -> null")
    @MethodSource("shouldReturnNullForUnparseable")
    @DisplayName("should return null for urls that cannot be parsed (no scheme)")
    void shouldReturnNullForUnparseableUrls(String input) {
        assertNull(normalizer.normalizeUrl(input));
    }

    // -------------------------------------------------------------------------
    // Pojedyncze przypadki brzegowe
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("should keep root slash for domain root")
    void shouldKeepRootSlashForDomainRoot() {
        assertEquals("https://example.com/", normalizer.normalizeUrl("https://www.example.com/"));
    }

    @Test
    @DisplayName("should remove trailing slash only for non-root path")
    void shouldRemoveTrailingSlashOnlyForNonRootPath() {
        assertEquals("https://example.com/contact", normalizer.normalizeUrl("https://example.com/contact/"));
    }

    @Test
    @DisplayName("should remove index file and convert empty path to root slash")
    void shouldConvertIndexFileToRootSlash() {
        assertEquals("https://example.com/", normalizer.normalizeUrl("https://example.com/index.html"));
    }

    @Test
    @DisplayName("should drop query and fragment during normalization")
    void shouldDropQueryAndFragment() {
        assertEquals("https://example.com/blog", normalizer.normalizeUrl("https://www.example.com/blog/?x=1#section"));
    }

    @Test
    @DisplayName("should handle http IDN url from real logs - hoflaedenfinder source")
    void shouldNormalizeRealWorldUmlautFromLogs() {
        // z logów: failed url=https://hofländle.de: unsupported URI
        String result = normalizer.normalizeUrl("https://hofländle.de");
        assertEquals("https://" + IDN.toASCII("hofländle.de") + "/", result);
    }
}