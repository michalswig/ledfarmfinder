package com.mike.leadfarmfinder.service.serpquery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mike.leadfarmfinder.service.OpenAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiSerpQueryImproverTest {

    @Mock
    private OpenAiService openAiService;

    private OpenAiSerpQueryImprover improver;

    @BeforeEach
    void setUp() {
        improver = new OpenAiSerpQueryImprover(
                openAiService,
                new ObjectMapper(),
                new SerpQueryImproverPromptBuilder()
        );
    }

    @Nested
    @DisplayName("suggestImprovements")
    class SuggestImprovementsTests {

        @Test
        @DisplayName("should return 3 suggestions when OpenAI returns valid JSON")
        void shouldReturn3SuggestionsForValidJson() {
            when(openAiService.classify(anyString())).thenReturn("""
                    {"queries": [
                        "Spargelhof Uckermark Kontakt Inhaber",
                        "auf unserem Hof Erdbeeren Barnim Adresse",
                        "Abholtermin Kartoffeln Havelland Telefon"
                    ]}
                    """);

            List<String> result = improver.suggestImprovements("weak query", 20);

            assertThat(result).hasSize(3);
            assertThat(result).contains(
                    "Spargelhof Uckermark Kontakt Inhaber",
                    "auf unserem Hof Erdbeeren Barnim Adresse",
                    "Abholtermin Kartoffeln Havelland Telefon"
            );
        }

        @Test
        @DisplayName("should return empty list when OpenAI returns empty string")
        void shouldReturnEmptyListWhenOpenAiReturnsEmpty() {
            when(openAiService.classify(anyString())).thenReturn("");

            List<String> result = improver.suggestImprovements("weak query", 20);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty list when OpenAI returns null")
        void shouldReturnEmptyListWhenOpenAiReturnsNull() {
            when(openAiService.classify(anyString())).thenReturn(null);

            List<String> result = improver.suggestImprovements("weak query", 20);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty list when JSON is malformed")
        void shouldReturnEmptyListWhenJsonIsMalformed() {
            when(openAiService.classify(anyString())).thenReturn("not a json {{{");

            List<String> result = improver.suggestImprovements("weak query", 20);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty list when queries field is missing")
        void shouldReturnEmptyListWhenQueriesFieldMissing() {
            when(openAiService.classify(anyString())).thenReturn("{\"something\": \"else\"}");

            List<String> result = improver.suggestImprovements("weak query", 20);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should skip blank entries in queries array")
        void shouldSkipBlankEntries() {
            when(openAiService.classify(anyString())).thenReturn("""
                    {"queries": ["valid query", "", "  ", "another valid"]}
                    """);

            List<String> result = improver.suggestImprovements("weak query", 20);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly("valid query", "another valid");
        }
    }
}