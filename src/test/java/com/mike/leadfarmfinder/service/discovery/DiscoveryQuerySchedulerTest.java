package com.mike.leadfarmfinder.service.discovery;

import com.mike.leadfarmfinder.config.LeadFinderProperties;
import com.mike.leadfarmfinder.entity.SerpQueryCursor;
import com.mike.leadfarmfinder.repository.SerpQueryCursorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscoveryQuerySchedulerTest {

    @Mock
    private SerpQueryCursorRepository serpQueryCursorRepository;

    @Mock
    private LeadFinderProperties leadFinderProperties;

    private DiscoveryQueryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new DiscoveryQueryScheduler(serpQueryCursorRepository, leadFinderProperties);
    }

    @Nested
    @DisplayName("pickNextNonExhaustedQuery")
    class PickNextNonExhaustedQueryTests {

        @Test
        @DisplayName("should return first non exhausted query")
        void shouldReturnFirstNonExhaustedQuery() {
            when(serpQueryCursorRepository.findByQuery("q1"))
                    .thenReturn(Optional.of(cursor("q1", 6, 5)));
            when(serpQueryCursorRepository.findByQuery("q2"))
                    .thenReturn(Optional.of(cursor("q2", 2, 5)));

            Optional<DiscoveryQueryScheduler.QueryPick> result =
                    scheduler.pickNextNonExhaustedQuery(List.of("q1", "q2"));

            assertThat(result).isPresent();
            assertThat(result.get().index()).isEqualTo(1);
            assertThat(result.get().query()).isEqualTo("q2");
            assertThat(result.get().cursor().getCurrentPage()).isEqualTo(2);
        }

        @Test
        @DisplayName("should return empty when all queries are exhausted")
        void shouldReturnEmptyWhenAllQueriesAreExhausted() {
            when(serpQueryCursorRepository.findByQuery("q1"))
                    .thenReturn(Optional.of(cursor("q1", 6, 5)));
            when(serpQueryCursorRepository.findByQuery("q2"))
                    .thenReturn(Optional.of(cursor("q2", 7, 5)));

            Optional<DiscoveryQueryScheduler.QueryPick> result =
                    scheduler.pickNextNonExhaustedQuery(List.of("q1", "q2"));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should create cursor when query does not exist")
        void shouldCreateCursorWhenQueryDoesNotExist() {
            LeadFinderProperties.Discovery discovery = new LeadFinderProperties.Discovery();
            discovery.setDefaultMaxSerpPage(10);

            when(leadFinderProperties.getDiscovery()).thenReturn(discovery);
            when(serpQueryCursorRepository.findByQuery("q1")).thenReturn(Optional.empty());
            when(serpQueryCursorRepository.save(any(SerpQueryCursor.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Optional<DiscoveryQueryScheduler.QueryPick> result =
                    scheduler.pickNextNonExhaustedQuery(List.of("q1"));

            assertThat(result).isPresent();
            assertThat(result.get().query()).isEqualTo("q1");
            assertThat(result.get().cursor().getCurrentPage()).isEqualTo(1);
            assertThat(result.get().cursor().getMaxPage()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("isExhausted")
    class IsExhaustedTests {

        @Test
        @DisplayName("should return true when current page is greater than max page")
        void shouldReturnTrueWhenCurrentPageIsGreaterThanMaxPage() {
            boolean result = scheduler.isExhausted(cursor("q1", 6, 5));

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when current page is not greater than max page")
        void shouldReturnFalseWhenCurrentPageIsNotGreaterThanMaxPage() {
            boolean result = scheduler.isExhausted(cursor("q1", 5, 5));

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("advancePageOrExhaust")
    class AdvancePageOrExhaustTests {

        @Test
        @DisplayName("should advance page when next page is within max page")
        void shouldAdvancePageWhenNextPageIsWithinMaxPage() {
            int result = scheduler.advancePageOrExhaust(3, 5);

            assertThat(result).isEqualTo(4);
        }

        @Test
        @DisplayName("should mark exhausted when next page exceeds max page")
        void shouldMarkExhaustedWhenNextPageExceedsMaxPage() {
            int result = scheduler.advancePageOrExhaust(5, 5);

            assertThat(result).isEqualTo(6);
        }
    }

    @Nested
    @DisplayName("saveCursorAfterRun")
    class SaveCursorAfterRunTests {

        @Test
        @DisplayName("should update cursor page and lastRunAt and save it")
        void shouldUpdateCursorPageAndLastRunAtAndSaveIt() {
            SerpQueryCursor cursor = cursor("q1", 1, 5);

            scheduler.saveCursorAfterRun(cursor, 4);

            ArgumentCaptor<SerpQueryCursor> captor = ArgumentCaptor.forClass(SerpQueryCursor.class);
            verify(serpQueryCursorRepository).save(captor.capture());

            SerpQueryCursor saved = captor.getValue();
            assertThat(saved.getCurrentPage()).isEqualTo(4);
            assertThat(saved.getLastRunAt()).isNotNull();
        }
    }

    private SerpQueryCursor cursor(String query, int currentPage, int maxPage) {
        SerpQueryCursor cursor = new SerpQueryCursor();
        cursor.setQuery(query);
        cursor.setCurrentPage(currentPage);
        cursor.setMaxPage(maxPage);
        return cursor;
    }
}