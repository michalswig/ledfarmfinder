package com.mike.leadfarmfinder.service.discovery;

import com.mike.leadfarmfinder.entity.DiscoveryRunStats;
import com.mike.leadfarmfinder.repository.DiscoveryRunStatsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DiscoveryRunStatsWriterTest {

    @Mock
    private DiscoveryRunStatsRepository discoveryRunStatsRepository;

    @Nested
    @DisplayName("save")
    class SaveTests {

        @Test
        @DisplayName("should build and save discovery run stats")
        void shouldBuildAndSaveDiscoveryRunStats() {
            DiscoveryRunStatsWriter writer = new DiscoveryRunStatsWriter(discoveryRunStatsRepository);

            LocalDateTime startedAt = LocalDateTime.now().minusMinutes(3);

            writer.save(
                    "spargelhof niedersachsen",
                    startedAt,
                    1,
                    3,
                    2,
                    20,
                    12,
                    4,
                    5,
                    1,
                    3
            );

            ArgumentCaptor<DiscoveryRunStats> captor = ArgumentCaptor.forClass(DiscoveryRunStats.class);
            verify(discoveryRunStatsRepository).save(captor.capture());

            DiscoveryRunStats stats = captor.getValue();
            assertThat(stats.getQuery()).isEqualTo("spargelhof niedersachsen");
            assertThat(stats.getStartedAt()).isEqualTo(startedAt);
            assertThat(stats.getFinishedAt()).isNotNull();
            assertThat(stats.getStartPage()).isEqualTo(1);
            assertThat(stats.getEndPage()).isEqualTo(3);
            assertThat(stats.getPagesVisited()).isEqualTo(2);
            assertThat(stats.getRawUrls()).isEqualTo(20);
            assertThat(stats.getCleanedUrls()).isEqualTo(12);
            assertThat(stats.getAcceptedUrls()).isEqualTo(4);
            assertThat(stats.getRejectedUrls()).isEqualTo(5);
            assertThat(stats.getErrors()).isEqualTo(1);
            assertThat(stats.getFilteredAlreadyDiscovered()).isEqualTo(3);
        }
    }
}