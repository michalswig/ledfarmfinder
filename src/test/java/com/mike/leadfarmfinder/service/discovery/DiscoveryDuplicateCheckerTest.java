package com.mike.leadfarmfinder.service.discovery;

import com.mike.leadfarmfinder.repository.DiscoveredUrlRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscoveryDuplicateCheckerTest {

    @Mock
    private DiscoveredUrlRepository repository;

    @Test
    void shouldReturnSeenByUrl() {
        when(repository.existsByUrl("url")).thenReturn(true);

        var checker = new DiscoveryDuplicateChecker(repository);

        var result = checker.checkAlreadySeen("url", "domain");

        assertThat(result).isEqualTo(DiscoveryDuplicateChecker.SeenDecision.SEEN_BY_URL);
        verify(repository, never()).existsByDomain(any());
    }

    @Test
    void shouldReturnSeenByDomain() {
        when(repository.existsByUrl("url")).thenReturn(false);
        when(repository.existsByDomain("domain")).thenReturn(true);

        var checker = new DiscoveryDuplicateChecker(repository);

        var result = checker.checkAlreadySeen("url", "domain");

        assertThat(result).isEqualTo(DiscoveryDuplicateChecker.SeenDecision.SEEN_BY_DOMAIN);
    }

    @Test
    void shouldReturnNotSeen() {
        when(repository.existsByUrl("url")).thenReturn(false);
        when(repository.existsByDomain("domain")).thenReturn(false);

        var checker = new DiscoveryDuplicateChecker(repository);

        var result = checker.checkAlreadySeen("url", "domain");

        assertThat(result).isEqualTo(DiscoveryDuplicateChecker.SeenDecision.NOT_SEEN);
    }
}