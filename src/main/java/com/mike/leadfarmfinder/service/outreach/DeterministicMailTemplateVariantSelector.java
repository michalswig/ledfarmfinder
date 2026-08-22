package com.mike.leadfarmfinder.service.outreach;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Deterministyczny wybór wariantu treści na podstawie ID leada.
 * <p>
 * Ten sam lead zawsze dostaje ten sam wariant (spójność przy follow-upach —
 * odbiorca nie dostaje dwóch różnych stylów "od tej samej osoby"),
 * a różni odbiorcy są rozproszeni po dostępnych wariantach.
 */
@Component
public class DeterministicMailTemplateVariantSelector implements MailTemplateVariantSelector {

    @Override
    public String select(Long leadId, List<String> variants) {
        Objects.requireNonNull(variants, "variants must not be null");
        if (variants.isEmpty()) {
            throw new IllegalArgumentException("variants must not be empty");
        }
        if (variants.size() == 1) {
            return variants.get(0);
        }

        long id = leadId != null ? leadId : 0L;
        int index = (int) Math.floorMod(id, (long) variants.size());
        return variants.get(index);
    }
}