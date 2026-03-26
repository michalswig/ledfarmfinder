package com.mike.leadfarmfinder.service.discovery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiscoveryUrlFilterTest {

    private final DiscoveryUrlNormalizer urlNormalizer = mock(DiscoveryUrlNormalizer.class);
    private final DiscoveryUrlFilter filter = new DiscoveryUrlFilter(urlNormalizer);

    @Nested
    class IsAllowedDomainTests {

        @Test
        @DisplayName("should return false when normalized domain is null")
        void shouldReturnFalseWhenDomainIsNull() {
            String url = "not-valid";
            when(urlNormalizer.extractNormalizedDomain(url)).thenReturn(null);

            boolean result = filter.isAllowedDomain(url);

            assertFalse(result);
        }

        @Test
        @DisplayName("should return false for blocked domain")
        void shouldReturnFalseForBlockedDomain() {
            String url = "https://facebook.com/page";
            when(urlNormalizer.extractNormalizedDomain(url)).thenReturn("facebook.com");

            boolean result = filter.isAllowedDomain(url);

            assertFalse(result);
        }

        @Test
        @DisplayName("should return false for hard negative domain")
        void shouldReturnFalseForHardNegativeDomain() {
            String url = "https://super-hotel-bayern.de";
            when(urlNormalizer.extractNormalizedDomain(url)).thenReturn("super-hotel-bayern.de");

            boolean result = filter.isAllowedDomain(url);

            assertFalse(result);
        }

        @Test
        @DisplayName("should return false when domain looks like media by containing news")
        void shouldReturnFalseForNewsLikeDomain() {
            String url = "https://regionalnews-example.de";
            when(urlNormalizer.extractNormalizedDomain(url)).thenReturn("regionalnews-example.de");

            boolean result = filter.isAllowedDomain(url);

            assertFalse(result);
        }

        @Test
        @DisplayName("should return false when domain contains zeitung")
        void shouldReturnFalseForZeitungLikeDomain() {
            String url = "https://lokalzeitung-example.de";
            when(urlNormalizer.extractNormalizedDomain(url)).thenReturn("lokalzeitung-example.de");

            boolean result = filter.isAllowedDomain(url);

            assertFalse(result);
        }

        @Test
        @DisplayName("should return true for allowed agricultural domain")
        void shouldReturnTrueForAllowedDomain() {
            String url = "https://spargelhof-muster.de";
            when(urlNormalizer.extractNormalizedDomain(url)).thenReturn("spargelhof-muster.de");

            boolean result = filter.isAllowedDomain(url);

            assertTrue(result);
        }
    }

    @Nested
    class IsHardNegativePathTests {

        @ParameterizedTest
        @CsvSource({
                "https://example.com/blog/post-1, true",
                "https://example.com/news/article, true",
                "https://example.com/karriere, true",
                "https://example.com/verzeichnis/firma, true",
                "https://example.com/urlaub/hof, true",
                "https://example.com/kontakt, false",
                "https://example.com/impressum, false",
                "https://example.com/produkte/spargel, false"
        })
        @DisplayName("should detect hard negative path tokens")
        void shouldDetectHardNegativePathTokens(String url, boolean expected) {
            boolean result = filter.isHardNegativePath(url);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("should return false when url cannot be parsed")
        void shouldReturnFalseWhenUrlCannotBeParsed() {
            boolean result = filter.isHardNegativePath("not a valid url");

            assertFalse(result);
        }

        @Test
        @DisplayName("should be case insensitive for path matching")
        void shouldBeCaseInsensitiveForPathMatching() {
            boolean result = filter.isHardNegativePath("https://example.com/BLOG/Post-1");

            assertTrue(result);
        }
    }

    @Nested
    class IsHardNegativeTests {

        @ParameterizedTest
        @CsvSource({
                "super-hotel-bayern.de, true",
                "regional-tourismus-portal.de, true",
                "jobvermittlung-direkt.de, true",
                "urlaub-auf-dem-bauernhof.de, true",
                "landwirtschaftlicher-betrieb.de, false",
                "spargelhof-muster.de, false",
                "erdbeerhof-test.de, false"
        })
        @DisplayName("should detect hard negative keywords in text")
        void shouldDetectHardNegativeKeywords(String text, boolean expected) {
            boolean result = filter.isHardNegative(text);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("should be case insensitive when checking hard negative text")
        void shouldBeCaseInsensitive() {
            boolean result = filter.isHardNegative("SUPER-HOTEL-BAYERN.DE");

            assertTrue(result);
        }
    }
}