package com.mike.leadfarmfinder.service.discovery;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class DiscoverySnippetFetcherTest {

    private final DiscoverySnippetFetcher fetcher = new DiscoverySnippetFetcher();

    @Nested
    @DisplayName("fetchTextSnippet")
    class FetchTextSnippetTests {

        @Test
        @DisplayName("should return trimmed text when fetched text is long enough and shorter than max length")
        void shouldReturnTrimmedTextWhenFetchedTextIsLongEnoughAndShorterThanMaxLength() throws Exception {
            String url = "https://farm.example.com";
            String longText = " ".repeat(5) + "a".repeat(150) + " ".repeat(5);

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
                Connection connection = mock(Connection.class);
                Document document = mock(Document.class);
                Elements elements = mock(Elements.class);

                jsoupMock.when(() -> Jsoup.connect(url)).thenReturn(connection);
                when(connection.userAgent(anyString())).thenReturn(connection);
                when(connection.timeout(10_000)).thenReturn(connection);
                when(connection.followRedirects(true)).thenReturn(connection);
                when(connection.get()).thenReturn(document);

                when(document.select("script,style,noscript")).thenReturn(elements);
                when(document.text()).thenReturn(longText);

                String result = fetcher.fetchTextSnippet(url);

                assertThat(result).hasSize(150);
                assertThat(result).isEqualTo("a".repeat(150));
            }
        }

        @Test
        @DisplayName("should return empty string when text is null")
        void shouldReturnEmptyStringWhenTextIsNull() throws Exception {
            String url = "https://farm.example.com";

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
                Connection connection = mock(Connection.class);
                Document document = mock(Document.class);
                Elements elements = mock(Elements.class);

                jsoupMock.when(() -> Jsoup.connect(url)).thenReturn(connection);
                when(connection.userAgent(anyString())).thenReturn(connection);
                when(connection.timeout(10_000)).thenReturn(connection);
                when(connection.followRedirects(true)).thenReturn(connection);
                when(connection.get()).thenReturn(document);

                when(document.select("script,style,noscript")).thenReturn(elements);
                when(document.text()).thenReturn(null);

                String result = fetcher.fetchTextSnippet(url);

                assertThat(result).isEmpty();
            }
        }

        @Test
        @DisplayName("should return empty string when trimmed text is shorter than minimum length")
        void shouldReturnEmptyStringWhenTrimmedTextIsShorterThanMinimumLength() throws Exception {
            String url = "https://farm.example.com";
            String shortText = "   short text   ";

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
                Connection connection = mock(Connection.class);
                Document document = mock(Document.class);
                Elements elements = mock(Elements.class);

                jsoupMock.when(() -> Jsoup.connect(url)).thenReturn(connection);
                when(connection.userAgent(anyString())).thenReturn(connection);
                when(connection.timeout(10_000)).thenReturn(connection);
                when(connection.followRedirects(true)).thenReturn(connection);
                when(connection.get()).thenReturn(document);

                when(document.select("script,style,noscript")).thenReturn(elements);
                when(document.text()).thenReturn(shortText);

                String result = fetcher.fetchTextSnippet(url);

                assertThat(result).isEmpty();
            }
        }

        @Test
        @DisplayName("should truncate text to max length when text is longer than 2000 characters")
        void shouldTruncateTextToMaxLengthWhenTextIsLongerThan2000Characters() throws Exception {
            String url = "https://farm.example.com";
            String veryLongText = "a".repeat(2500);

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
                Connection connection = mock(Connection.class);
                Document document = mock(Document.class);
                Elements elements = mock(Elements.class);

                jsoupMock.when(() -> Jsoup.connect(url)).thenReturn(connection);
                when(connection.userAgent(anyString())).thenReturn(connection);
                when(connection.timeout(10_000)).thenReturn(connection);
                when(connection.followRedirects(true)).thenReturn(connection);
                when(connection.get()).thenReturn(document);

                when(document.select("script,style,noscript")).thenReturn(elements);
                when(document.text()).thenReturn(veryLongText);

                String result = fetcher.fetchTextSnippet(url);

                assertThat(result).hasSize(2000);
                assertThat(result).isEqualTo("a".repeat(2000));
            }
        }

        @Test
        @DisplayName("should return empty string when unsupported mime type is thrown")
        void shouldReturnEmptyStringWhenUnsupportedMimeTypeIsThrown() throws Exception {
            String url = "https://farm.example.com/file.pdf";

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
                Connection connection = mock(Connection.class);

                jsoupMock.when(() -> Jsoup.connect(url)).thenReturn(connection);
                when(connection.userAgent(anyString())).thenReturn(connection);
                when(connection.timeout(10_000)).thenReturn(connection);
                when(connection.followRedirects(true)).thenReturn(connection);
                when(connection.get()).thenThrow(new UnsupportedMimeTypeException("unsupported", "application/pdf", url));

                String result = fetcher.fetchTextSnippet(url);

                assertThat(result).isEmpty();
            }
        }

        @Test
        @DisplayName("should return empty string when generic exception is thrown")
        void shouldReturnEmptyStringWhenGenericExceptionIsThrown() throws Exception {
            String url = "https://farm.example.com";

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
                Connection connection = mock(Connection.class);

                jsoupMock.when(() -> Jsoup.connect(url)).thenReturn(connection);
                when(connection.userAgent(anyString())).thenReturn(connection);
                when(connection.timeout(10_000)).thenReturn(connection);
                when(connection.followRedirects(true)).thenReturn(connection);
                when(connection.get()).thenThrow(new IOException("connection failed"));

                String result = fetcher.fetchTextSnippet(url);

                assertThat(result).isEmpty();
            }
        }
    }
}