package com.mike.leadfarmfinder.service.discovery;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscoverySnippetFetcherTest {

    @Mock
    private DiscoveryContentTypeChecker contentTypeChecker;

    @InjectMocks
    private DiscoverySnippetFetcher fetcher;

    private static DiscoveryContentTypeResult probeOkHtml() {
        return new DiscoveryContentTypeResult(
                true,
                DiscoveryContentTypeResult.Reason.OK,
                Optional.of("text/html")
        );
    }

    @Nested
    @DisplayName("fetchTextSnippet")
    class FetchTextSnippetTests {

        @Test
        @DisplayName("should return trimmed text when fetched text is long enough and shorter than max length")
        void shouldReturnTrimmedTextWhenFetchedTextIsLongEnoughAndShorterThanMaxLength() throws Exception {
            String url = "https://farm.example.com";
            String longText = " ".repeat(5) + "a".repeat(150) + " ".repeat(5);

            when(contentTypeChecker.check(url)).thenReturn(probeOkHtml());

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

            when(contentTypeChecker.check(url)).thenReturn(probeOkHtml());

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

            when(contentTypeChecker.check(url)).thenReturn(probeOkHtml());

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

            when(contentTypeChecker.check(url)).thenReturn(probeOkHtml());

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

            when(contentTypeChecker.check(url)).thenReturn(probeOkHtml());

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

            when(contentTypeChecker.check(url)).thenReturn(probeOkHtml());

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

        @Test
        @DisplayName("should skip Jsoup when precheck says PDF")
        void shouldSkipJsoupWhenPrecheckSaysPdf() {
            String url = "https://farm.example.com/paper.pdf";
            when(contentTypeChecker.check(url)).thenReturn(
                    new DiscoveryContentTypeResult(
                            false,
                            DiscoveryContentTypeResult.Reason.PDF,
                            Optional.of("application/pdf")
                    )
            );

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
                String result = fetcher.fetchTextSnippet(url);
                assertThat(result).isEmpty();
                jsoupMock.verify(() -> Jsoup.connect(anyString()), never());
            }
        }

        @Test
        @DisplayName("should skip Jsoup when precheck says non-HTML")
        void shouldSkipJsoupWhenPrecheckSaysNonHtml() {
            String url = "https://farm.example.com/binary";
            when(contentTypeChecker.check(url)).thenReturn(
                    new DiscoveryContentTypeResult(
                            false,
                            DiscoveryContentTypeResult.Reason.NON_HTML,
                            Optional.of("application/octet-stream")
                    )
            );

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
                String result = fetcher.fetchTextSnippet(url);
                assertThat(result).isEmpty();
                jsoupMock.verify(() -> Jsoup.connect(anyString()), never());
            }
        }

        @Test
        @DisplayName("should call Jsoup when precheck is UNKNOWN")
        void shouldCallJsoupWhenPrecheckUnknown() throws Exception {
            String url = "https://farm.example.com";
            String longText = "a".repeat(150);

            when(contentTypeChecker.check(url))
                    .thenReturn(DiscoveryContentTypeResult.unknown());

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

                assertThat(result).isEqualTo(longText);
                verify(contentTypeChecker).check(url);
                jsoupMock.verify(() -> Jsoup.connect(url));
            }
        }
    }
}
