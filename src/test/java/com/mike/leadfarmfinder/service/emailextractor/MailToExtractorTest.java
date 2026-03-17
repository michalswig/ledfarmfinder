package com.mike.leadfarmfinder.service.emailextractor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailToExtractorTest {

    @Mock
    private TextObfuscationNormalizer obfuscationNormalizer;

    @InjectMocks
    private MailToExtractor extractor;

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
        @DisplayName("should return empty list when html does not contain mailto")
        void shouldReturnEmptyListWhenHtmlDoesNotContainMailto() {
            String html = """
                    <html>
                        <body>
                            <a href="https://example.com/contact">Contact</a>
                        </body>
                    </html>
                    """;

            List<String> result = extractor.extractCandidates(html);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should extract normalized email from mailto link")
        void shouldExtractNormalizedEmailFromMailtoLink() {
            String html = """
                    <a href="mailto:info@example.com">Email us</a>
                    """;

            when(obfuscationNormalizer.normalize("info@example.com"))
                    .thenReturn("info@example.com");

            List<String> result = extractor.extractCandidates(html);

            assertThat(result)
                    .hasSize(1)
                    .containsExactly("info@example.com");
        }

        @Test
        @DisplayName("should cut query params before normalization")
        void shouldCutQueryParamsBeforeNormalization() {
            String html = """
                    <a href="mailto:info@example.com?subject=Hello">Email us</a>
                    """;

            when(obfuscationNormalizer.normalize("info@example.com"))
                    .thenReturn("info@example.com");

            List<String> result = extractor.extractCandidates(html);

            assertThat(result)
                    .hasSize(1)
                    .containsExactly("info@example.com");
        }

        @Test
        @DisplayName("should normalize obfuscated mailto email")
        void shouldNormalizeObfuscatedMailtoEmail() {
            String html = """
                    <a href="mailto:info(at)example(dot)com">Email us</a>
                    """;

            when(obfuscationNormalizer.normalize("info(at)example(dot)com"))
                    .thenReturn("info@example.com");

            List<String> result = extractor.extractCandidates(html);

            assertThat(result)
                    .hasSize(1)
                    .containsExactly("info@example.com");
        }

        @Test
        @DisplayName("should skip blank normalized values")
        void shouldSkipBlankNormalizedValues() {
            String html = """
                    <a href="mailto:info@example.com">Email us</a>
                    """;

            when(obfuscationNormalizer.normalize("info@example.com"))
                    .thenReturn(" ");

            List<String> result = extractor.extractCandidates(html);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should extract multiple normalized emails in order")
        void shouldExtractMultipleNormalizedEmailsInOrder() {
            String html = """
                    <a href="mailto:first@example.com">First</a>
                    <a href="mailto:second@example.com?subject=Hi">Second</a>
                    """;

            when(obfuscationNormalizer.normalize("first@example.com"))
                    .thenReturn("first@example.com");
            when(obfuscationNormalizer.normalize("second@example.com"))
                    .thenReturn("second@example.com");

            List<String> result = extractor.extractCandidates(html);

            assertThat(result)
                    .hasSize(2)
                    .containsExactly(
                            "first@example.com",
                            "second@example.com"
                    );
        }

        @Test
        @DisplayName("should match mailto case insensitively")
        void shouldMatchMailtoCaseInsensitively() {
            String html = """
                    <a href="MAILTO:info@example.com">Email us</a>
                    """;

            when(obfuscationNormalizer.normalize("info@example.com"))
                    .thenReturn("info@example.com");

            List<String> result = extractor.extractCandidates(html);

            assertThat(result)
                    .hasSize(1)
                    .containsExactly("info@example.com");
        }
    }
}