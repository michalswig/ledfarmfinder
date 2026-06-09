package com.mike.leadfarmfinder.service.osm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OsmFarmSourceTest {

    @Mock
    private OverpassApiClient overpassApiClient;

    @Mock
    private OsmProperties osmProperties;

    private OsmFarmSource osmFarmSource;

    @BeforeEach
    void setUp() {
        osmFarmSource = new OsmFarmSource(overpassApiClient, osmProperties);
    }

    @Test
    @DisplayName("sourceName() should return openstreetmap.org")
    void sourceNameShouldReturnOpenStreetMap() {
        assertThat(osmFarmSource.sourceName()).isEqualTo("openstreetmap.org");
    }

    @Nested
    @DisplayName("fetchFarmUrls — disabled flag")
    class DisabledTests {

        @Test
        @DisplayName("should return empty list and not call Overpass when disabled")
        void shouldReturnEmptyListWhenDisabled() {
            when(osmProperties.isEnabled()).thenReturn(false);

            List<String> urls = osmFarmSource.fetchFarmUrls();

            assertThat(urls).isEmpty();
            verifyNoInteractions(overpassApiClient);
        }
    }

    @Nested
    @DisplayName("fetchFarmUrls — enabled")
    class EnabledTests {

        @BeforeEach
        void enable() {
            when(osmProperties.isEnabled()).thenReturn(true);
        }

        @Test
        @DisplayName("should return sorted URLs from Overpass")
        void shouldReturnSortedUrls() {
            when(overpassApiClient.fetchFarmWebsites()).thenReturn(List.of(
                    "https://zzz-farm.de",
                    "https://aaa-farm.de",
                    "https://mmm-farm.de"
            ));

            List<String> urls = osmFarmSource.fetchFarmUrls();

            // OsmFarmSource sorts the URLs from OverpassApiClient
            // (OverpassApiClient also sorts, but OsmFarmSource adds its own sort as safety net)
            assertThat(urls).containsExactly(
                    "https://aaa-farm.de",
                    "https://mmm-farm.de",
                    "https://zzz-farm.de"
            );
        }

        @Test
        @DisplayName("should return empty list when Overpass returns empty")
        void shouldReturnEmptyListWhenOverpassReturnsEmpty() {
            when(overpassApiClient.fetchFarmWebsites()).thenReturn(List.of());

            List<String> urls = osmFarmSource.fetchFarmUrls();

            assertThat(urls).isEmpty();
        }

        @Test
        @DisplayName("should call OverpassApiClient.fetchFarmWebsites() exactly once")
        void shouldCallOverpassExactlyOnce() {
            when(overpassApiClient.fetchFarmWebsites()).thenReturn(List.of("https://farm.de"));

            osmFarmSource.fetchFarmUrls();

            verify(overpassApiClient, times(1)).fetchFarmWebsites();
        }

        @Test
        @DisplayName("should return already sorted list unchanged")
        void shouldReturnAlreadySortedListUnchanged() {
            List<String> alreadySorted = List.of(
                    "https://alpha.de",
                    "https://beta.de",
                    "https://gamma.de"
            );
            when(overpassApiClient.fetchFarmWebsites()).thenReturn(alreadySorted);

            List<String> urls = osmFarmSource.fetchFarmUrls();

            assertThat(urls).containsExactlyElementsOf(alreadySorted);
        }

        @Test
        @DisplayName("should handle single URL correctly")
        void shouldHandleSingleUrl() {
            when(overpassApiClient.fetchFarmWebsites()).thenReturn(List.of("https://single-farm.de"));

            List<String> urls = osmFarmSource.fetchFarmUrls();

            assertThat(urls).containsExactly("https://single-farm.de");
        }
    }
}