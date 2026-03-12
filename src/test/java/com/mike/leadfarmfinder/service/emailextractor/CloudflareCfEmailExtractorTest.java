package com.mike.leadfarmfinder.service.emailextractor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CloudflareCfEmailExtractorTest {

    private final CloudflareCfEmailExtractor extractor = new CloudflareCfEmailExtractor();

    @Nested
    @DisplayName("extractCandidates")
    class ExtractCandidatesTest {

        @Test
        @DisplayName("should return empty list when html is null")
        void shouldReturnEmptyListWhenHtmlIsNull() {
            List<String> result = extractor.extractCandidates(null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty list when html is blank")
        void shouldReturnEmptyListWhenHtmlIsBlank() {
            List<String> result = extractor.extractCandidates("   ");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty list when html does not contain Cloudflare protected email")
        void shouldReturnEmptyListWhenHtmlDoesNotContainCloudflareProtectedEmail() {
            String html = """
                    <html>
                        <body>
                            <a href="mailto:info@example.com">info@example.com</a>
                            <p>No Cloudflare email here</p>
                        </body>
                    </html>
                    """;

            List<String> result = extractor.extractCandidates(html);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should extract decoded email when html contains one Cloudflare protected email")
        void shouldExtractDecodedEmailWhenHtmlContainsOneCloudflareProtectedEmail() {
            String html = """
                    <a data-cfemail="6f060109002f0a170e021f030a410c0002"></a>
                    """;

            List<String> result = extractor.extractCandidates(html);

            assertThat(result)
                    .hasSize(1)
                    .containsExactly("info@example.com");
        }

        @Test
        @DisplayName("should extract multiple decoded emails when html contains many Cloudflare protected emails")
        void shouldExtractMultipleDecodedEmailsWhenHtmlContainsManyCloudflareProtectedEmails() {
            String html = """
                    <a data-cfemail="6f060109002f0a170e021f030a410c0002"></a>
                    <a data-cfemail="6f060109002f0a170e021f030a410c0002"></a>
                    """;

            List<String> result = extractor.extractCandidates(html);

            assertThat(result)
                    .hasSize(2)
                    .containsExactly(
                            "info@example.com",
                            "info@example.com"
                    );
        }

        @Test
        @DisplayName("should ignore invalid decoded values when Cloudflare decoder returns blank result")
        void shouldIgnoreInvalidDecodedValuesWhenCloudflareDecoderReturnsBlankResult() {
            String html = """
                    <a data-cfemail=""></a>
                    """;

            List<String> result = extractor.extractCandidates(html);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should preserve extraction order")
        void shouldPreserveExtractionOrder() {
            String html = """
                    <div>
                        <a data-cfemail="6f060109002f0a170e021f030a410c0002"></a>
                        <span>some text</span>
                        <a data-cfemail="6f060109002f0a170e021f030a410c0002"></a>
                    </div>
                    """;

            List<String> result = extractor.extractCandidates(html);

            assertThat(result).containsExactly(
                    "info@example.com",
                    "info@example.com"
            );
        }
    }
}