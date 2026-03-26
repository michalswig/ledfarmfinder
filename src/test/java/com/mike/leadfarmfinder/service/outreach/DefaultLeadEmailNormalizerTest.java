package com.mike.leadfarmfinder.service.outreach;

import com.mike.leadfarmfinder.entity.FarmLead;
import com.mike.leadfarmfinder.repository.FarmLeadRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultLeadEmailNormalizerTest {

    @Mock
    private FarmLeadRepository farmLeadRepository;

    @Mock
    private FarmLead lead;

    @InjectMocks
    private DefaultLeadEmailNormalizer normalizer;

    @Nested
    @DisplayName("normalizeAndValidateOrDeactivate")
    class NormalizeAndValidateOrDeactivateTest {

        @Test
        @DisplayName("should return normalized email when email is valid")
        void shouldReturnNormalizedEmailWhenEmailIsValid() {
            when(lead.getEmail()).thenReturn("Info@Example.DE");

            String result = normalizer.normalizeAndValidateOrDeactivate(lead);

            assertThat(result).isEqualTo("info@example.de");
            verify(farmLeadRepository, never()).save(any());
            verify(lead, never()).setActive(false);
            verify(lead, never()).setBounce(true);
        }

        @Test
        @DisplayName("should trim and lowercase valid email")
        void shouldTrimAndLowercaseValidEmail() {
            when(lead.getEmail()).thenReturn("  SALES@Example.DE  ");

            String result = normalizer.normalizeAndValidateOrDeactivate(lead);

            assertThat(result).isEqualTo("sales@example.de");
            verify(farmLeadRepository, never()).save(any());
            verify(lead, never()).setActive(false);
            verify(lead, never()).setBounce(true);
        }

        @Test
        @DisplayName("should remove trailing comma from valid email")
        void shouldRemoveTrailingCommaFromValidEmail() {
            when(lead.getEmail()).thenReturn("info@example.de,");

            String result = normalizer.normalizeAndValidateOrDeactivate(lead);

            assertThat(result).isEqualTo("info@example.de");
            verify(farmLeadRepository, never()).save(any());
            verify(lead, never()).setActive(false);
            verify(lead, never()).setBounce(true);
        }

        @Test
        @DisplayName("should remove trailing semicolon from valid email")
        void shouldRemoveTrailingSemicolonFromValidEmail() {
            when(lead.getEmail()).thenReturn("info@example.de;");

            String result = normalizer.normalizeAndValidateOrDeactivate(lead);

            assertThat(result).isEqualTo("info@example.de");
            verify(farmLeadRepository, never()).save(any());
            verify(lead, never()).setActive(false);
            verify(lead, never()).setBounce(true);
        }

        @Test
        @DisplayName("should cut off everything starting from www after at sign")
        void shouldCutOffEverythingStartingFromWwwAfterAtSign() {
            when(lead.getEmail()).thenReturn("info@example.dewww.example.de");

            String result = normalizer.normalizeAndValidateOrDeactivate(lead);

            assertThat(result).isEqualTo("info@example.de");
            verify(farmLeadRepository, never()).save(any());
            verify(lead, never()).setActive(false);
            verify(lead, never()).setBounce(true);
        }

        @Test
        @DisplayName("should deactivate lead when email is null")
        void shouldDeactivateLeadWhenEmailIsNull() {
            when(lead.getEmail()).thenReturn(null);
            when(lead.getId()).thenReturn(1L);

            String result = normalizer.normalizeAndValidateOrDeactivate(lead);

            assertThat(result).isNull();
            verify(lead).setActive(false);
            verify(lead).setBounce(true);
            verify(farmLeadRepository).save(lead);
        }

        @Test
        @DisplayName("should deactivate lead when email is blank")
        void shouldDeactivateLeadWhenEmailIsBlank() {
            when(lead.getEmail()).thenReturn("   ");
            when(lead.getId()).thenReturn(1L);

            String result = normalizer.normalizeAndValidateOrDeactivate(lead);

            assertThat(result).isNull();
            verify(lead).setActive(false);
            verify(lead).setBounce(true);
            verify(farmLeadRepository).save(lead);
        }

        @Test
        @DisplayName("should deactivate lead when normalized email has invalid format")
        void shouldDeactivateLeadWhenNormalizedEmailHasInvalidFormat() {
            when(lead.getEmail()).thenReturn("not-an-email");
            when(lead.getId()).thenReturn(1L);

            String result = normalizer.normalizeAndValidateOrDeactivate(lead);

            assertThat(result).isNull();
            verify(lead).setActive(false);
            verify(lead).setBounce(true);
            verify(farmLeadRepository).save(lead);
        }
    }
}