package com.mike.leadfarmfinder.service.discovery;

import com.mike.leadfarmfinder.repository.DiscoveredUrlRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscoveryDuplicateCheckerTest {

    @Mock
    private DiscoveredUrlRepository discoveredUrlRepository;

    @Nested
    @DisplayName("checkAlreadySeen")
    class CheckAlreadySeenTests {

        @Test
        @DisplayName("should return SEEN_BY_URL when repository contains url")
        void shouldReturnSeenByUrlWhenRepositoryContainsUrl() {
            String normalizedUrl = "https://farm.example.com";

            when(discoveredUrlRepository.existsByUrl(normalizedUrl)).thenReturn(true);

            DiscoveryDuplicateChecker checker = new DiscoveryDuplicateChecker(discoveredUrlRepository);

            DiscoveryDuplicateChecker.SeenDecision result = checker.checkAlreadySeen(normalizedUrl);

            assertThat(result).isEqualTo(DiscoveryDuplicateChecker.SeenDecision.SEEN_BY_URL);
        }

        @Test
        @DisplayName("should return NOT_SEEN when repository does not contain url")
        void shouldReturnNotSeenWhenRepositoryDoesNotContainUrl() {
            String normalizedUrl = "https://newfarm.example.com";

            when(discoveredUrlRepository.existsByUrl(normalizedUrl)).thenReturn(false);

            DiscoveryDuplicateChecker checker = new DiscoveryDuplicateChecker(discoveredUrlRepository);

            DiscoveryDuplicateChecker.SeenDecision result = checker.checkAlreadySeen(normalizedUrl);

            assertThat(result).isEqualTo(DiscoveryDuplicateChecker.SeenDecision.NOT_SEEN);
        }
    }
}