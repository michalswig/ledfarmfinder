package com.mike.leadfarmfinder.service.outreach;

import com.mike.leadfarmfinder.config.OutreachProperties;
import com.mike.leadfarmfinder.entity.FarmLead;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultLeadEligibilityPolicyTest {

    @Mock
    private OutreachProperties outreachProperties;

    @Mock
    private FarmLead lead;

    @InjectMocks
    private DefaultLeadEligibilityPolicy policy;

    private final LocalDateTime now = LocalDateTime.of(2026, 3, 12, 12, 0);

    @Nested
    @DisplayName("isEligibleOrLog")
    class IsEligibleOrLogTest {

        @Test
        @DisplayName("should return false when lead is null")
        void shouldReturnFalseWhenLeadIsNull() {
            boolean result = policy.isEligibleOrLog(null, EmailType.FIRST, now);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when lead is inactive")
        void shouldReturnFalseWhenLeadIsInactive() {
            when(lead.isActive()).thenReturn(false);
            when(lead.getId()).thenReturn(1L);
            when(lead.getEmail()).thenReturn("info@example.com");

            boolean result = policy.isEligibleOrLog(lead, EmailType.FIRST, now);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when lead is marked as bounce")
        void shouldReturnFalseWhenLeadIsMarkedAsBounce() {
            when(lead.isActive()).thenReturn(true);
            when(lead.isBounce()).thenReturn(true);
            when(lead.getId()).thenReturn(1L);
            when(lead.getEmail()).thenReturn("info@example.com");

            boolean result = policy.isEligibleOrLog(lead, EmailType.FIRST, now);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true for first email when lead is active and not bounced")
        void shouldReturnTrueForFirstEmailWhenLeadIsActiveAndNotBounced() {
            when(lead.isActive()).thenReturn(true);
            when(lead.isBounce()).thenReturn(false);

            boolean result = policy.isEligibleOrLog(lead, EmailType.FIRST, now);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false for follow up when first email was not sent")
        void shouldReturnFalseForFollowUpWhenFirstEmailWasNotSent() {
            when(lead.isActive()).thenReturn(true);
            when(lead.isBounce()).thenReturn(false);
            when(lead.getFirstEmailSentAt()).thenReturn(null);
            when(lead.getId()).thenReturn(1L);
            when(lead.getEmail()).thenReturn("info@example.com");

            boolean result = policy.isEligibleOrLog(lead, EmailType.FOLLOW_UP, now);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true for follow up when first email was sent and last email is null")
        void shouldReturnTrueForFollowUpWhenFirstEmailWasSentAndLastEmailIsNull() {
            when(lead.isActive()).thenReturn(true);
            when(lead.isBounce()).thenReturn(false);
            when(lead.getFirstEmailSentAt()).thenReturn(now.minusDays(10));
            when(lead.getLastEmailSentAt()).thenReturn(null);

            boolean result = policy.isEligibleOrLog(lead, EmailType.FOLLOW_UP, now);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false for follow up when last email is too recent")
        void shouldReturnFalseForFollowUpWhenLastEmailIsTooRecent() {
            when(lead.isActive()).thenReturn(true);
            when(lead.isBounce()).thenReturn(false);
            when(lead.getFirstEmailSentAt()).thenReturn(now.minusDays(10));
            when(lead.getLastEmailSentAt()).thenReturn(now.minusDays(2));
            when(lead.getId()).thenReturn(1L);
            when(lead.getEmail()).thenReturn("info@example.com");
            when(outreachProperties.getFollowUpMinDaysSinceLastEmail()).thenReturn(3);

            boolean result = policy.isEligibleOrLog(lead, EmailType.FOLLOW_UP, now);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true for follow up when last email is old enough")
        void shouldReturnTrueForFollowUpWhenLastEmailIsOldEnough() {
            when(lead.isActive()).thenReturn(true);
            when(lead.isBounce()).thenReturn(false);
            when(lead.getFirstEmailSentAt()).thenReturn(now.minusDays(10));
            when(lead.getLastEmailSentAt()).thenReturn(now.minusDays(5));
            when(outreachProperties.getFollowUpMinDaysSinceLastEmail()).thenReturn(3);

            boolean result = policy.isEligibleOrLog(lead, EmailType.FOLLOW_UP, now);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true for follow up when last email is exactly on cutoff")
        void shouldReturnTrueForFollowUpWhenLastEmailIsExactlyOnCutoff() {
            when(lead.isActive()).thenReturn(true);
            when(lead.isBounce()).thenReturn(false);
            when(lead.getFirstEmailSentAt()).thenReturn(now.minusDays(10));
            when(outreachProperties.getFollowUpMinDaysSinceLastEmail()).thenReturn(3);
            when(lead.getLastEmailSentAt()).thenReturn(now.minusDays(3));

            boolean result = policy.isEligibleOrLog(lead, EmailType.FOLLOW_UP, now);

            assertThat(result).isTrue();
        }
    }
}