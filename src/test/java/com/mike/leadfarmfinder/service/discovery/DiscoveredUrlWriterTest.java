package com.mike.leadfarmfinder.service.discovery;

import com.mike.leadfarmfinder.dto.FarmClassificationResult;
import com.mike.leadfarmfinder.entity.DiscoveredUrl;
import com.mike.leadfarmfinder.repository.DiscoveredUrlRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscoveredUrlWriterTest {

    @Mock
    private DiscoveredUrlRepository discoveredUrlRepository;

    @Mock
    private DiscoveryUrlNormalizer urlNormalizer;

    @Nested
    @DisplayName("save")
    class SaveTests {

        @Test
        @DisplayName("should create new discovered url when url does not exist")
        void shouldCreateNewDiscoveredUrlWhenUrlDoesNotExist() {
            DiscoveredUrlWriter writer = new DiscoveredUrlWriter(discoveredUrlRepository, urlNormalizer);

            String url = "https://farm.example.com";
            FarmClassificationResult result = new FarmClassificationResult(true, false, "farm", null);

            when(discoveredUrlRepository.findByUrl(url)).thenReturn(Optional.empty());
            when(urlNormalizer.extractNormalizedDomain(url)).thenReturn("farm.example.com");
            when(discoveredUrlRepository.save(any(DiscoveredUrl.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            writer.save(url, result);

            ArgumentCaptor<DiscoveredUrl> captor = ArgumentCaptor.forClass(DiscoveredUrl.class);
            verify(discoveredUrlRepository).save(captor.capture());

            DiscoveredUrl saved = captor.getValue();
            assertThat(saved.getUrl()).isEqualTo(url);
            assertThat(saved.getDomain()).isEqualTo("farm.example.com");
            assertThat(saved.isFarm()).isTrue();
            assertThat(saved.isSeasonalJobs()).isFalse();
            assertThat(saved.getFirstSeenAt()).isNotNull();
            assertThat(saved.getLastSeenAt()).isNotNull();
        }

        @Test
        @DisplayName("should update existing discovered url when url already exists")
        void shouldUpdateExistingDiscoveredUrlWhenUrlAlreadyExists() {
            DiscoveredUrlWriter writer = new DiscoveredUrlWriter(discoveredUrlRepository, urlNormalizer);

            String url = "https://farm.example.com";
            FarmClassificationResult result = new FarmClassificationResult(false, true, "seasonal jobs", null);

            DiscoveredUrl existing = new DiscoveredUrl();
            existing.setId(10L);
            existing.setUrl(url);
            existing.setDomain("old.example.com");
            existing.setFarm(true);
            existing.setSeasonalJobs(false);

            LocalDateTime firstSeen = LocalDateTime.now().minusDays(10);
            existing.setFirstSeenAt(firstSeen);
            existing.setLastSeenAt(LocalDateTime.now().minusDays(1));

            when(discoveredUrlRepository.findByUrl(url)).thenReturn(Optional.of(existing));
            when(urlNormalizer.extractNormalizedDomain(url)).thenReturn("farm.example.com");
            when(discoveredUrlRepository.save(any(DiscoveredUrl.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            writer.save(url, result);

            ArgumentCaptor<DiscoveredUrl> captor = ArgumentCaptor.forClass(DiscoveredUrl.class);
            verify(discoveredUrlRepository).save(captor.capture());

            DiscoveredUrl saved = captor.getValue();
            assertThat(saved.getId()).isEqualTo(10L);
            assertThat(saved.getUrl()).isEqualTo(url);
            assertThat(saved.getDomain()).isEqualTo("farm.example.com");
            assertThat(saved.isFarm()).isFalse();
            assertThat(saved.isSeasonalJobs()).isTrue();
            assertThat(saved.getFirstSeenAt()).isEqualTo(firstSeen);
            assertThat(saved.getLastSeenAt()).isNotNull();
        }

        @Test
        @DisplayName("should swallow exception when repository save fails")
        void shouldSwallowExceptionWhenRepositorySaveFails() {
            DiscoveredUrlWriter writer = new DiscoveredUrlWriter(discoveredUrlRepository, urlNormalizer);

            String url = "https://farm.example.com";
            FarmClassificationResult result = new FarmClassificationResult(true, false, "farm", null);

            when(discoveredUrlRepository.findByUrl(url)).thenReturn(Optional.empty());
            when(urlNormalizer.extractNormalizedDomain(url)).thenReturn("farm.example.com");
            when(discoveredUrlRepository.save(any(DiscoveredUrl.class)))
                    .thenThrow(new RuntimeException("db error"));

            writer.save(url, result);

            verify(discoveredUrlRepository).save(any(DiscoveredUrl.class));
        }

        @Test
        @DisplayName("should not set firstSeenAt again for existing entity")
        void shouldNotSetFirstSeenAtAgainForExistingEntity() {
            DiscoveredUrlWriter writer = new DiscoveredUrlWriter(discoveredUrlRepository, urlNormalizer);

            String url = "https://farm.example.com";
            FarmClassificationResult result = new FarmClassificationResult(true, false, "farm", null);

            DiscoveredUrl existing = new DiscoveredUrl();
            existing.setId(5L);
            LocalDateTime firstSeen = LocalDateTime.now().minusDays(20);
            existing.setFirstSeenAt(firstSeen);

            when(discoveredUrlRepository.findByUrl(url)).thenReturn(Optional.of(existing));
            when(urlNormalizer.extractNormalizedDomain(url)).thenReturn("farm.example.com");
            when(discoveredUrlRepository.save(any(DiscoveredUrl.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            writer.save(url, result);

            ArgumentCaptor<DiscoveredUrl> captor = ArgumentCaptor.forClass(DiscoveredUrl.class);
            verify(discoveredUrlRepository).save(captor.capture());

            assertThat(captor.getValue().getFirstSeenAt()).isEqualTo(firstSeen);
        }
    }
}