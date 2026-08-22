package com.mike.leadfarmfinder.service.outreach;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeterministicMailTemplateVariantSelectorTest {

    private final DeterministicMailTemplateVariantSelector selector =
            new DeterministicMailTemplateVariantSelector();

    @Test
    void sameLeadIdAlwaysReturnsSameVariant() {
        List<String> variants = List.of("A", "B", "C");

        String first = selector.select(42L, variants);
        String second = selector.select(42L, variants);

        assertEquals(first, second);
    }

    @Test
    void distributesDifferentLeadIdsAcrossVariants() {
        List<String> variants = List.of("A", "B", "C");

        assertEquals("A", selector.select(0L, variants));
        assertEquals("B", selector.select(1L, variants));
        assertEquals("C", selector.select(2L, variants));
        assertEquals("A", selector.select(3L, variants));
    }

    @Test
    void singleVariantAlwaysReturned() {
        List<String> variants = List.of("only one");

        assertEquals("only one", selector.select(999L, variants));
    }

    @Test
    void emptyVariantsThrows() {
        List<String> variants = List.of();

        assertThrows(IllegalArgumentException.class, () -> selector.select(1L, variants));
    }

    @Test
    void nullLeadIdTreatedAsZero() {
        List<String> variants = List.of("A", "B");

        assertEquals("A", selector.select(null, variants));
    }
}