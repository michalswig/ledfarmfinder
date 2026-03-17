package com.mike.leadfarmfinder.service.emailextractor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegexTextExtractorTest {

    private final RegexTextExtractor extractor = new RegexTextExtractor();

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
        @DisplayName("should return empty list when text does not contain any email")
        void shouldReturnEmptyListWhenTextDoesNotContainAnyEmail() {
            String html = """
                    <html>
                        <body>
                            <p>Contact us via form only.</p>
                        </body>
                    </html>
                    """;

            List<String> result = extractor.extractCandidates(html);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should extract single email from text")
        void shouldExtractSingleEmailFromText() {
            String html = """
                    <p>Contact: info@example.com</p>
                    """;

            List<String> result = extractor.extractCandidates(html);

            assertThat(result)
                    .hasSize(1)
                    .containsExactly("info@example.com");
        }

        @Test
        @DisplayName("should extract multiple emails in order")
        void shouldExtractMultipleEmailsInOrder() {
            String html = """
                    <p>Sales: sales@example.com</p>
                    <p>Support: support@test.de</p>
                    <p>Office: office@company.org</p>
                    """;

            List<String> result = extractor.extractCandidates(html);

            assertThat(result)
                    .hasSize(3)
                    .containsExactly(
                            "sales@example.com",
                            "support@test.de",
                            "office@company.org"
                    );
        }

        @Test
        @DisplayName("should extract email surrounded by html content")
        void shouldExtractEmailSurroundedByHtmlContent() {
            String html = """
                    <div>
                        Reach us at <strong>hello@domain.com</strong> for details.
                    </div>
                    """;

            List<String> result = extractor.extractCandidates(html);

            assertThat(result)
                    .containsExactly("hello@domain.com");
        }

        @Test
        @DisplayName("should extract duplicated emails when they appear multiple times")
        void shouldExtractDuplicatedEmailsWhenTheyAppearMultipleTimes() {
            String html = """
                    <p>info@example.com</p>
                    <p>info@example.com</p>
                    """;

            List<String> result = extractor.extractCandidates(html);

            assertThat(result)
                    .hasSize(2)
                    .containsExactly(
                            "info@example.com",
                            "info@example.com"
                    );
        }
    }
}