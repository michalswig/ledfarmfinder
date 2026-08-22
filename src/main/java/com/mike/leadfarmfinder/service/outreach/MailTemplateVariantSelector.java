package com.mike.leadfarmfinder.service.outreach;

import java.util.List;

/**
 * Wybiera konkretny wariant treści maila spośród dostępnych szablonów.
 * <p>
 * Wydzielone jako osobny port, żeby strategię wyboru (deterministyczna,
 * losowa, A/B-testowa w przyszłości) dało się podmienić niezależnie
 * od renderowania i wysyłki maila (SRP) oraz łatwo przetestować
 * jednostkowo bez mockowania całego {@code DefaultMailComposer}.
 */
public interface MailTemplateVariantSelector {

    /**
     * @param leadId   identyfikator leada, używany jako klucz stabilności wyboru;
     *                 może być {@code null} (traktowane jak {@code 0})
     * @param variants niepusta lista dostępnych wariantów treści
     * @return wybrany wariant treści (jeden z elementów {@code variants})
     * @throws IllegalArgumentException gdy {@code variants} jest {@code null} lub puste
     */
    String select(Long leadId, List<String> variants);
}