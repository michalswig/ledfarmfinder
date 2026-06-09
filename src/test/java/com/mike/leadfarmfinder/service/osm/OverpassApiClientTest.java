package com.mike.leadfarmfinder.service.osm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OverpassApiClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private OsmProperties osmProperties;

    private OverpassApiClient client;

    @BeforeEach
    void setUp() {
        client = new OverpassApiClient(restTemplate, new ObjectMapper(), osmProperties);
        when(osmProperties.getBbox()).thenReturn("47.3,5.9,55.1,15.0");
        when(osmProperties.getOverpassUrl()).thenReturn("https://overpass-api.de/api/interpreter");
    }

    @Nested
    @DisplayName("fetchFarmWebsites — JSON parsing")
    class ParsingTests {

        @Test
        @DisplayName("should parse node with website tag")
        void shouldParseNodeWithWebsite() {
            String json = """
                    {
                      "elements": [
                        {
                          "type": "node",
                          "id": 1,
                          "tags": {
                            "shop": "farm",
                            "name": "Spargelhof Müller",
                            "website": "https://spargelhof-mueller.de"
                          }
                        }
                      ]
                    }
                    """;

            mockResponse(json);

            List<String> urls = client.fetchFarmWebsites();

            assertThat(urls).containsExactly("https://spargelhof-mueller.de");
        }

        @Test
        @DisplayName("should parse way with contact:website tag")
        void shouldParseWayWithContactWebsite() {
            String json = """
                    {
                      "elements": [
                        {
                          "type": "way",
                          "id": 42,
                          "center": { "lat": 48.1, "lon": 11.5 },
                          "tags": {
                            "shop": "farm",
                            "contact:website": "http://obsthof-bauer.de"
                          }
                        }
                      ]
                    }
                    """;

            mockResponse(json);

            List<String> urls = client.fetchFarmWebsites();

            assertThat(urls).containsExactly("http://obsthof-bauer.de");
        }

        @Test
        @DisplayName("should parse relation with out center")
        void shouldParseRelationWithOutCenter() {
            String json = """
                    {
                      "elements": [
                        {
                          "type": "relation",
                          "id": 99,
                          "center": { "lat": 51.2, "lon": 7.0 },
                          "tags": {
                            "shop": "farm",
                            "website": "https://biohof-relation.de"
                          }
                        }
                      ]
                    }
                    """;

            mockResponse(json);

            List<String> urls = client.fetchFarmWebsites();

            assertThat(urls).containsExactly("https://biohof-relation.de");
        }

        @Test
        @DisplayName("should prefer website over contact:website when both present")
        void shouldPreferWebsiteOverContactWebsite() {
            String json = """
                    {
                      "elements": [
                        {
                          "type": "node",
                          "id": 1,
                          "tags": {
                            "shop": "farm",
                            "website": "https://primary.de",
                            "contact:website": "https://secondary.de"
                          }
                        }
                      ]
                    }
                    """;

            mockResponse(json);

            List<String> urls = client.fetchFarmWebsites();

            assertThat(urls).containsExactly("https://primary.de");
        }

        @Test
        @DisplayName("should add https:// prefix when URL has no scheme")
        void shouldNormalizeUrlWithNoScheme() {
            String json = """
                    {
                      "elements": [
                        {
                          "type": "node",
                          "id": 1,
                          "tags": {
                            "shop": "farm",
                            "website": "hofladen-ohne-schema.de"
                          }
                        }
                      ]
                    }
                    """;

            mockResponse(json);

            List<String> urls = client.fetchFarmWebsites();

            assertThat(urls).containsExactly("https://hofladen-ohne-schema.de");
        }

        @Test
        @DisplayName("should skip element with no website or contact:website")
        void shouldSkipElementWithNoWebsite() {
            String json = """
                    {
                      "elements": [
                        {
                          "type": "node",
                          "id": 1,
                          "tags": {
                            "shop": "farm",
                            "name": "Hofladen ohne Website"
                          }
                        }
                      ]
                    }
                    """;

            mockResponse(json);

            List<String> urls = client.fetchFarmWebsites();

            assertThat(urls).isEmpty();
        }

        @Test
        @DisplayName("should skip element with blank website value")
        void shouldSkipBlankWebsite() {
            String json = """
                    {
                      "elements": [
                        {
                          "type": "node",
                          "id": 1,
                          "tags": {
                            "shop": "farm",
                            "website": "   "
                          }
                        }
                      ]
                    }
                    """;

            mockResponse(json);

            List<String> urls = client.fetchFarmWebsites();

            assertThat(urls).isEmpty();
        }

        @Test
        @DisplayName("should return sorted URLs for stable dedup")
        void shouldReturnSortedUrls() {
            String json = """
                    {
                      "elements": [
                        {
                          "type": "node",
                          "id": 3,
                          "tags": { "shop": "farm", "website": "https://zzz-farm.de" }
                        },
                        {
                          "type": "node",
                          "id": 1,
                          "tags": { "shop": "farm", "website": "https://aaa-farm.de" }
                        },
                        {
                          "type": "node",
                          "id": 2,
                          "tags": { "shop": "farm", "website": "https://mmm-farm.de" }
                        }
                      ]
                    }
                    """;

            mockResponse(json);

            List<String> urls = client.fetchFarmWebsites();

            assertThat(urls).containsExactly(
                    "https://aaa-farm.de",
                    "https://mmm-farm.de",
                    "https://zzz-farm.de"
            );
        }

        @Test
        @DisplayName("should return empty list when elements array is empty")
        void shouldReturnEmptyListWhenNoElements() {
            String json = """
                    {
                      "elements": []
                    }
                    """;

            mockResponse(json);

            List<String> urls = client.fetchFarmWebsites();

            assertThat(urls).isEmpty();
        }
    }

    @Nested
    @DisplayName("fetchFarmWebsites — error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should return empty list when response is null")
        void shouldReturnEmptyListWhenResponseIsNull() {
            when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(null);

            List<String> urls = client.fetchFarmWebsites();

            assertThat(urls).isEmpty();
        }

        @Test
        @DisplayName("should return empty list when response is blank")
        void shouldReturnEmptyListWhenResponseIsBlank() {
            when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn("   ");

            List<String> urls = client.fetchFarmWebsites();

            assertThat(urls).isEmpty();
        }

        @Test
        @DisplayName("should return empty list on HTTP error")
        void shouldReturnEmptyListOnHttpError() {
            when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(new RestClientException("Connection refused"));

            List<String> urls = client.fetchFarmWebsites();

            assertThat(urls).isEmpty();
        }

        @Test
        @DisplayName("should return empty list on invalid JSON")
        void shouldReturnEmptyListOnInvalidJson() {
            mockResponse("not valid json {{{");

            List<String> urls = client.fetchFarmWebsites();

            assertThat(urls).isEmpty();
        }
    }

    @Nested
    @DisplayName("fetchFarmWebsites — Locale.US in bbox")
    class LocaleTests {

        @Test
        @DisplayName("should use Locale.US so bbox decimals use dot not comma")
        void shouldUseLocaleUsForBbox() {
            // bbox "47.3,5.9,55.1,15.0" — na polskim locale bez Locale.US
            // String.format %f daje "47,300000" zamiast "47.300000"
            // Overpass zwróciłoby błąd parsowania.
            // Tutaj weryfikujemy że request body zawiera kropki dziesiętne.
            String json = """
                    {
                      "elements": []
                    }
                    """;

            // Przechwytujemy wysłany request body
            final String[] capturedBody = {null};

            when(restTemplate.postForObject(
                    anyString(),
                    argThat((HttpEntity<?> entity) -> {
                        capturedBody[0] = (String) entity.getBody();
                        return true;
                    }),
                    eq(String.class)
            )).thenReturn(json);

            client.fetchFarmWebsites();

            // URL-encoded body musi zawierać wartości bbox z kropkami
            assertThat(capturedBody[0]).isNotNull();
            // Po URL-encoding '.' pozostaje '.', ',' to '%2C'
            // Sprawdzamy że bbox nie zawiera przecinka w miejsu kropki dziesiętnej
            // np. "47.3" po URL-encode to "47.3" a nie "47%2C3"
            assertThat(capturedBody[0]).contains("47.3");
            assertThat(capturedBody[0]).contains("55.1");
        }
    }

    // --- helpers ---

    private void mockResponse(String json) {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(json);
    }
}