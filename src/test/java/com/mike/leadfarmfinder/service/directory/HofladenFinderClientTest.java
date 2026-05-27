package com.mike.leadfarmfinder.service.directory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HofladenFinderClientTest {

    @Mock
    private RestTemplate restTemplate;

    private HofladenFinderClient client;

    @BeforeEach
    void setUp() {
        HofladenFinderProperties properties = new HofladenFinderProperties(
                "https://www.hofladenfinder.org/farmshop/search",
                200,
                List.of("spargel")
        );
        client = new HofladenFinderClient(restTemplate, properties);
    }

    @Nested
    @DisplayName("normalizeWebsite")
    class NormalizeWebsiteTests {

        @Test
        @DisplayName("adds https to bare domain")
        void addsHttpsToBareDomain() {
            assertThat(HofladenFinderClient.normalizeWebsite("www.bauerklaus.de"))
                    .isEqualTo("https://www.bauerklaus.de");
        }

        @Test
        @DisplayName("does not modify url that already has https")
        void doesNotModifyHttpsUrl() {
            assertThat(HofladenFinderClient.normalizeWebsite("https://bauerklaus.de"))
                    .isEqualTo("https://bauerklaus.de");
        }

        @Test
        @DisplayName("does not modify url that already has http")
        void doesNotModifyHttpUrl() {
            assertThat(HofladenFinderClient.normalizeWebsite("http://bauerklaus.de"))
                    .isEqualTo("http://bauerklaus.de");
        }

        @Test
        @DisplayName("returns null for null website")
        void returnsNullForNull() {
            assertThat(HofladenFinderClient.normalizeWebsite(null)).isNull();
        }

        @Test
        @DisplayName("returns null for blank website")
        void returnsNullForBlank() {
            assertThat(HofladenFinderClient.normalizeWebsite("   ")).isNull();
        }

        @Test
        @DisplayName("trims whitespace before normalizing")
        void trimsWhitespace() {
            assertThat(HofladenFinderClient.normalizeWebsite("  www.bauerklaus.de  "))
                    .isEqualTo("https://www.bauerklaus.de");
        }
    }

    @Nested
    @DisplayName("fetchFarmUrls")
    class FetchFarmUrlsTests {

        @Test
        @DisplayName("returns empty list when API returns null response")
        void returnsEmptyWhenNullResponse() {
            when(restTemplate.getForObject(any(), eq(HofladenFinderResponse.class)))
                    .thenReturn(null);

            assertThat(client.fetchFarmUrls()).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when API throws exception")
        void returnsEmptyOnHttpError() {
            when(restTemplate.getForObject(any(), eq(HofladenFinderResponse.class)))
                    .thenThrow(new RestClientException("timeout"));

            assertThat(client.fetchFarmUrls()).isEmpty();
        }

        @Test
        @DisplayName("filters out locations with null website")
        void filtersNullWebsites() {
            HofladenFinderLocation withWebsite = new HofladenFinderLocation(
                    1L, "Farm A", "www.farm-a.de", "Berlin");
            HofladenFinderLocation noWebsite = new HofladenFinderLocation(
                    2L, "Farm B", null, "Hamburg");

            when(restTemplate.getForObject(any(), eq(HofladenFinderResponse.class)))
                    .thenReturn(new HofladenFinderResponse(List.of(withWebsite, noWebsite), 2))
                    .thenReturn(new HofladenFinderResponse(List.of(), 2));

            assertThat(client.fetchFarmUrls())
                    .containsExactly("https://www.farm-a.de");
        }

        @Test
        @DisplayName("deduplicates same website appearing in multiple locations")
        void deduplicatesSameWebsite() {
            // bauer-bossmann.de pojawia się dla 3 lokalizacji — realny przypadek z API
            HofladenFinderLocation loc1 = new HofladenFinderLocation(
                    1L, "Bossmann Haan", "www.bauer-bossmann.de", "Haan");
            HofladenFinderLocation loc2 = new HofladenFinderLocation(
                    2L, "Bossmann Erkrath", "www.bauer-bossmann.de", "Erkrath");
            HofladenFinderLocation loc3 = new HofladenFinderLocation(
                    3L, "Bossmann Leichlingen", "www.bauer-bossmann.de", "Leichlingen");

            when(restTemplate.getForObject(any(), eq(HofladenFinderResponse.class)))
                    .thenReturn(new HofladenFinderResponse(List.of(loc1, loc2, loc3), 3))
                    .thenReturn(new HofladenFinderResponse(List.of(), 3));

            assertThat(client.fetchFarmUrls())
                    .hasSize(1)
                    .containsExactly("https://www.bauer-bossmann.de");
        }

        @Test
        @DisplayName("stops pagination when TotalCount is reached")
        void stopsPaginationWhenTotalCountReached() {
            HofladenFinderLocation loc = new HofladenFinderLocation(
                    1L, "Farm", "www.farm.de", "Berlin");

            // TotalCount=1, PAGE_SIZE=10 — po stronie 1 wiemy że nie ma strony 2
            // Mockito strict mode rzuci UnnecessaryStubbingException jeśli page 2 zostanie zawołana
            when(restTemplate.getForObject(any(), eq(HofladenFinderResponse.class)))
                    .thenReturn(new HofladenFinderResponse(List.of(loc), 1));

            assertThat(client.fetchFarmUrls())
                    .containsExactly("https://www.farm.de");
        }
    }
}