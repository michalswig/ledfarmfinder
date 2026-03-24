package com.mike.leadfarmfinder.service.discovery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiscoveryUrlScorerTest {

    private final DiscoveryUrlNormalizer urlNormalizer = mock(DiscoveryUrlNormalizer.class);
    private final DiscoveryUrlFilter urlFilter = mock(DiscoveryUrlFilter.class);

    private final DiscoveryUrlScorer scorer = new DiscoveryUrlScorer(urlNormalizer, urlFilter);

    @Test
    @DisplayName("should return 0 when normalized domain is null")
    void shouldReturnZeroWhenNormalizedDomainIsNull() {
        when(urlNormalizer.extractNormalizedDomain("https://example.com")).thenReturn(null);

        int score = scorer.computeDomainPriorityScore("https://example.com");

        assertEquals(0, score);
        verify(urlNormalizer).extractNormalizedDomain("https://example.com");
        verifyNoInteractions(urlFilter);
    }

    @Test
    @DisplayName("should return minus one hundred when domain is hard negative")
    void shouldReturnMinusOneHundredWhenDomainIsHardNegative() {
        when(urlNormalizer.extractNormalizedDomain("https://booking.com")).thenReturn("booking.com");
        when(urlFilter.isHardNegative("booking.com")).thenReturn(true);

        int score = scorer.computeDomainPriorityScore("https://booking.com");

        assertEquals(-100, score);
        verify(urlNormalizer).extractNormalizedDomain("https://booking.com");
        verify(urlFilter).isHardNegative("booking.com");
    }

    @Test
    @DisplayName("should add score for farm related domain")
    void shouldAddScoreForFarmRelatedDomain() {
        when(urlNormalizer.extractNormalizedDomain("https://spargelhof-meyer.de")).thenReturn("spargelhof-meyer.de");
        when(urlFilter.isHardNegative("spargelhof-meyer.de")).thenReturn(false);

        int score = scorer.computeDomainPriorityScore("https://spargelhof-meyer.de");

        assertTrue(score >= 50);
    }

    @Test
    @DisplayName("should add bonus for short german domain")
    void shouldAddBonusForShortGermanDomain() {
        when(urlNormalizer.extractNormalizedDomain("https://hof.de")).thenReturn("hof.de");
        when(urlFilter.isHardNegative("hof.de")).thenReturn(false);

        int score = scorer.computeDomainPriorityScore("https://hof.de");

        assertEquals(35, score);
    }

    @Test
    @DisplayName("should penalize shop domain but still keep farm bonuses")
    void shouldPenalizeShopDomainButStillKeepFarmBonuses() {
        when(urlNormalizer.extractNormalizedDomain("https://hofladen-shop.de")).thenReturn("hofladen-shop.de");
        when(urlFilter.isHardNegative("hofladen-shop.de")).thenReturn(false);

        int score = scorer.computeDomainPriorityScore("https://hofladen-shop.de");

        assertTrue(score > 0);
        assertTrue(score < 60);
    }

    @Test
    @DisplayName("should penalize soft negative hosting token")
    void shouldPenalizeSoftNegativeHostingToken() {
        when(urlNormalizer.extractNormalizedDomain("https://spargelhof-wordpress.de")).thenReturn("spargelhof-wordpress.de");
        when(urlFilter.isHardNegative("spargelhof-wordpress.de")).thenReturn(false);

        int score = scorer.computeDomainPriorityScore("https://spargelhof-wordpress.de");

        assertTrue(score > 0);
        verify(urlFilter).isHardNegative("spargelhof-wordpress.de");
    }

    @Test
    @DisplayName("should add bonus for contact hint in url")
    void shouldAddBonusForContactHintInUrl() {
        when(urlNormalizer.extractNormalizedDomain("https://example.de/kontakt")).thenReturn("example.de");
        when(urlFilter.isHardNegative("example.de")).thenReturn(false);

        int score = scorer.computeDomainPriorityScore("https://example.de/kontakt");

        assertEquals(23, score);
    }

    @Test
    @DisplayName("should combine bonuses and penalties")
    void shouldCombineBonusesAndPenalties() {
        when(urlNormalizer.extractNormalizedDomain("https://spargelhof-wordpress.de/kontakt"))
                .thenReturn("spargelhof-wordpress.de");
        when(urlFilter.isHardNegative("spargelhof-wordpress.de")).thenReturn(false);

        int score = scorer.computeDomainPriorityScore("https://spargelhof-wordpress.de/kontakt");

        assertTrue(score > 20);
    }

    @Test
    @DisplayName("should detect farm looking domain")
    void shouldDetectFarmLookingDomain() {
        when(urlFilter.isHardNegative("spargelhof-meyer.de")).thenReturn(false);

        boolean result = scorer.looksLikeFarmDomain("spargelhof-meyer.de");

        assertTrue(result);
        verify(urlFilter).isHardNegative("spargelhof-meyer.de");
    }

    @Test
    @DisplayName("should return false for hard negative domain even if keyword exists")
    void shouldReturnFalseForHardNegativeDomainEvenIfKeywordExists() {
        when(urlFilter.isHardNegative("tourismus-hof.de")).thenReturn(true);

        boolean result = scorer.looksLikeFarmDomain("tourismus-hof.de");

        assertFalse(result);
    }

    @Test
    @DisplayName("should return true when text contains farm keyword")
    void shouldReturnTrueWhenTextContainsFarmKeyword() {
        assertTrue(scorer.hasFarmKeyword("spargelhof-meyer.de"));
        assertTrue(scorer.hasFarmKeyword("BIOHOF-MUELLER.DE"));
        assertTrue(scorer.hasFarmKeyword("kartoffelhof"));
    }

    @Test
    @DisplayName("should return false when text does not contain farm keyword")
    void shouldReturnFalseWhenTextDoesNotContainFarmKeyword() {
        assertFalse(scorer.hasFarmKeyword("booking.com"));
        assertFalse(scorer.hasFarmKeyword("stadtmarketing.de"));
        assertFalse(scorer.hasFarmKeyword("random-domain.test"));
    }
}