package com.mike.leadfarmfinder.service.discovery;

import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
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

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.security.cert.CertPathValidatorException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
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

    private Connection mockConnectionChain(MockedStatic<Jsoup> jsoupMock, String url) throws IOException {
        Connection connection = mock(Connection.class);
        jsoupMock.when(() -> Jsoup.connect(url)).thenReturn(connection);
        when(connection.userAgent(anyString())).thenReturn(connection);
        when(connection.referrer(anyString())).thenReturn(connection);
        when(connection.timeout(anyInt())).thenReturn(connection);
        when(connection.followRedirects(true)).thenReturn(connection);
        when(connection.ignoreHttpErrors(false)).thenReturn(connection);
        return connection;
    }

    private Connection mockConnectionChainForAnyUrl(MockedStatic<Jsoup> jsoupMock) throws IOException {
        Connection connection = mock(Connection.class);
        jsoupMock.when(() -> Jsoup.connect(anyString())).thenReturn(connection);
        when(connection.userAgent(anyString())).thenReturn(connection);
        when(connection.referrer(anyString())).thenReturn(connection);
        when(connection.timeout(anyInt())).thenReturn(connection);
        when(connection.followRedirects(true)).thenReturn(connection);
        when(connection.ignoreHttpErrors(false)).thenReturn(connection);
        return connection;
    }

    private void mockDocumentWithText(Connection connection, String text) throws IOException {
        Document document = mock(Document.class);
        Elements elements = mock(Elements.class);
        when(connection.get()).thenReturn(document);
        when(document.select("script,style,noscript")).thenReturn(elements);
        when(document.text()).thenReturn(text);
    }

    @Nested
    @DisplayName("fetchTextSnippet — basic behavior")
    class BasicBehaviorTests {

        @Test
        @DisplayName("should return trimmed text when fetched text is long enough")
        void shouldReturnTrimmedTextWhenFetchedTextIsLongEnough() throws Exception {
            String url = "https://farm.example.com";
            String longText = " ".repeat(5) + "a".repeat(150) + " ".repeat(5);

            when(contentTypeChecker.check(anyString())).thenReturn(probeOkHtml());

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
                Connection connection = mockConnectionChain(jsoupMock, url);
                mockDocumentWithText(connection, longText);

                String result = fetcher.fetchTextSnippet(url);

                assertThat(result).hasSize(150);
                assertThat(result).isEqualTo("a".repeat(150));
            }
        }

        @Test
        @DisplayName("should return empty string when trimmed text is shorter than minimum length")
        void shouldReturnEmptyStringWhenTextTooShort() throws Exception {
            String url = "https://farm.example.com";

            when(contentTypeChecker.check(anyString())).thenReturn(probeOkHtml());

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
                Connection connection = mockConnectionChain(jsoupMock, url);
                mockDocumentWithText(connection, "   short text   ");

                String result = fetcher.fetchTextSnippet(url);

                assertThat(result).isEmpty();
            }
        }

        @Test
        @DisplayName("should truncate text to max length when text exceeds 2000 characters")
        void shouldTruncateTextWhenExceedsMaxLength() throws Exception {
            String url = "https://farm.example.com";
            String veryLongText = "a".repeat(2500);

            when(contentTypeChecker.check(anyString())).thenReturn(probeOkHtml());

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
                Connection connection = mockConnectionChain(jsoupMock, url);
                mockDocumentWithText(connection, veryLongText);

                String result = fetcher.fetchTextSnippet(url);

                assertThat(result).hasSize(2000);
            }
        }

        @Test
        @DisplayName("should return empty string when text is null")
        void shouldReturnEmptyStringWhenTextIsNull() throws Exception {
            String url = "https://farm.example.com";

            when(contentTypeChecker.check(anyString())).thenReturn(probeOkHtml());

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
                Connection connection = mockConnectionChain(jsoupMock, url);
                mockDocumentWithText(connection, null);

                String result = fetcher.fetchTextSnippet(url);

                assertThat(result).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("fetchTextSnippet — content type precheck")
    class ContentTypePrecheckTests {

        @Test
        @DisplayName("should skip Jsoup when precheck says PDF")
        void shouldSkipJsoupWhenPrecheckSaysPdf() {
            String url = "https://farm.example.com/paper.pdf";

            when(contentTypeChecker.check(anyString())).thenReturn(
                    new DiscoveryContentTypeResult(
                            false,
                            DiscoveryContentTypeResult.Reason.PDF,
                            Optional.of("application/pdf")
                    )
            );

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
                String result = fetcher.fetchTextSnippet(url);

                assertThat(result).isEmpty();
                jsoupMock.verifyNoInteractions();
            }
        }

        @Test
        @DisplayName("should skip Jsoup when precheck says non-HTML")
        void shouldSkipJsoupWhenPrecheckSaysNonHtml() {
            String url = "https://farm.example.com/binary";

            when(contentTypeChecker.check(anyString())).thenReturn(
                    new DiscoveryContentTypeResult(
                            false,
                            DiscoveryContentTypeResult.Reason.NON_HTML,
                            Optional.of("application/octet-stream")
                    )
            );

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
                String result = fetcher.fetchTextSnippet(url);

                assertThat(result).isEmpty();
                jsoupMock.verifyNoInteractions();
            }
        }

        @Test
        @DisplayName("should call Jsoup when precheck is UNKNOWN")
        void shouldCallJsoupWhenPrecheckUnknown() throws Exception {
            String url = "https://farm.example.com";
            String longText = "a".repeat(150);

            when(contentTypeChecker.check(anyString()))
                    .thenReturn(DiscoveryContentTypeResult.unknown());

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
                Connection connection = mockConnectionChain(jsoupMock, url);
                mockDocumentWithText(connection, longText);

                String result = fetcher.fetchTextSnippet(url);

                assertThat(result).isEqualTo(longText);
                verify(contentTypeChecker).check(url);
                jsoupMock.verify(() -> Jsoup.connect(url));
            }
        }
    }

    @Nested
    @DisplayName("fetchTextSnippet — HTTP error handling")
    class HttpErrorHandlingTests {

        @Test
        @DisplayName("should return empty string when unsupported mime type is thrown")
        void shouldReturnEmptyOnUnsupportedMimeType() throws Exception {
            String url = "https://farm.example.com";

            when(contentTypeChecker.check(anyString())).thenReturn(probeOkHtml());

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
                Connection connection = mockConnectionChain(jsoupMock, url);
                when(connection.get()).thenThrow(
                        new UnsupportedMimeTypeException("nope", "application/pdf", url)
                );

                String result = fetcher.fetchTextSnippet(url);

                assertThat(result).isEmpty();
            }
        }

        @Test
        @DisplayName("should return empty string when generic IOException is thrown")
        void shouldReturnEmptyOnGenericIoException() throws Exception {
            String url = "https://farm.example.com";

            when(contentTypeChecker.check(anyString())).thenReturn(probeOkHtml());

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
                Connection connection = mockConnectionChain(jsoupMock, url);
                when(connection.get()).thenThrow(new IOException("connection failed"));

                String result = fetcher.fetchTextSnippet(url);

                assertThat(result).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("fetchTextSnippet — host-level blocking (403)")
    class HostBlocking403Tests {

        @Test
        @DisplayName("should skip all remaining candidates from same host after first 403")
        void shouldSkipRemainingCandidatesAfterFirst403() throws Exception {
            String url = "https://blocked-farm.de/produkte";

            when(contentTypeChecker.check(anyString())).thenReturn(probeOkHtml());

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
                Connection connection = mockConnectionChainForAnyUrl(jsoupMock);
                when(connection.get()).thenThrow(
                        new HttpStatusException("Forbidden", 403, url)
                );

                String result = fetcher.fetchTextSnippet(url);

                assertThat(result).isEmpty();

                // 403 on first candidate → blocked → skip 7 remaining candidates
                // Only 1 Jsoup.connect call (for the original URL), not 8
                jsoupMock.verify(() -> Jsoup.connect(anyString()), times(1));
            }
        }
    }

    @Nested
    @DisplayName("fetchTextSnippet — host-level blocking (SSL/TLS)")
    class HostBlockingSslTests {

        @Test
        @DisplayName("should skip all remaining candidates when SSL certificate is expired")
        void shouldSkipRemainingCandidatesOnExpiredSslCertificate() throws Exception {
            String url = "https://zehmerhof.de/pages/spargel";

            when(contentTypeChecker.check(anyString())).thenReturn(probeOkHtml());

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
                Connection connection = mockConnectionChainForAnyUrl(jsoupMock);

                SSLHandshakeException sslError = new SSLHandshakeException(
                        "PKIX path validation failed"
                );
                sslError.initCause(new CertPathValidatorException("validity check failed"));
                when(connection.get()).thenThrow(sslError);

                String result = fetcher.fetchTextSnippet(url);

                assertThat(result).isEmpty();

                // SSL error on first attempt → host blocked → skip remaining candidates
                jsoupMock.verify(() -> Jsoup.connect(anyString()), times(1));
            }
        }

        @Test
        @DisplayName("should skip all remaining candidates when SSL handshake fails")
        void shouldSkipRemainingCandidatesOnSslHandshakeFailure() throws Exception {
            String url = "https://broken-ssl-farm.de";

            when(contentTypeChecker.check(anyString())).thenReturn(probeOkHtml());

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
                Connection connection = mockConnectionChainForAnyUrl(jsoupMock);
                when(connection.get()).thenThrow(
                        new SSLHandshakeException("Remote host terminated the handshake")
                );

                String result = fetcher.fetchTextSnippet(url);

                assertThat(result).isEmpty();
                jsoupMock.verify(() -> Jsoup.connect(anyString()), times(1));
            }
        }
    }

    @Nested
    @DisplayName("fetchTextSnippet — host-level blocking (connection)")
    class HostBlockingConnectionTests {

        @Test
        @DisplayName("should skip all remaining candidates when host is unknown (DNS failure)")
        void shouldSkipRemainingCandidatesOnUnknownHost() throws Exception {
            String url = "https://nonexistent-farm.de";

            when(contentTypeChecker.check(anyString())).thenReturn(probeOkHtml());

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
                Connection connection = mockConnectionChainForAnyUrl(jsoupMock);
                when(connection.get()).thenThrow(
                        new IOException("java.net.UnknownHostException: nonexistent-farm.de",
                                new UnknownHostException("nonexistent-farm.de"))
                );

                String result = fetcher.fetchTextSnippet(url);

                assertThat(result).isEmpty();
                jsoupMock.verify(() -> Jsoup.connect(anyString()), times(1));
            }
        }

        @Test
        @DisplayName("should skip all remaining candidates when connection is refused")
        void shouldSkipRemainingCandidatesOnConnectionRefused() throws Exception {
            String url = "https://offline-farm.de";

            when(contentTypeChecker.check(anyString())).thenReturn(probeOkHtml());

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
                Connection connection = mockConnectionChainForAnyUrl(jsoupMock);
                when(connection.get()).thenThrow(
                        new IOException("Connection refused",
                                new ConnectException("Connection refused"))
                );

                String result = fetcher.fetchTextSnippet(url);

                assertThat(result).isEmpty();
                jsoupMock.verify(() -> Jsoup.connect(anyString()), times(1));
            }
        }
    }

    @Nested
    @DisplayName("fetchTextSnippet — non-host-level errors should NOT block host")
    class NonHostBlockingTests {

        @Test
        @DisplayName("should NOT block host on 404 and should try other candidates")
        void shouldNotBlockHostOn404() throws Exception {
            String url = "https://farm.example.com/missing-page";

            when(contentTypeChecker.check(anyString())).thenReturn(probeOkHtml());

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
                Connection connection = mockConnectionChainForAnyUrl(jsoupMock);
                when(connection.get()).thenThrow(
                        new HttpStatusException("Not Found", 404, url)
                );

                String result = fetcher.fetchTextSnippet(url);

                assertThat(result).isEmpty();

                // 404 is NOT host-level → should try all 8 candidates
                jsoupMock.verify(() -> Jsoup.connect(anyString()), times(8));
            }
        }

        @Test
        @DisplayName("should NOT block host on generic timeout and should retry")
        void shouldNotBlockHostOnTimeout() throws Exception {
            String url = "https://slow-farm.de";

            when(contentTypeChecker.check(anyString())).thenReturn(probeOkHtml());

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
                Connection connection = mockConnectionChainForAnyUrl(jsoupMock);
                when(connection.get()).thenThrow(
                        new IOException("Read timed out")
                );

                String result = fetcher.fetchTextSnippet(url);

                assertThat(result).isEmpty();

                // Timeout is NOT host-level → retries (2 attempts per candidate × 7 unique candidates = 14)
                // 7 not 8 because original URL = root URL, LinkedHashSet deduplicates
                jsoupMock.verify(() -> Jsoup.connect(anyString()), times(14));
            }
        }
    }
}