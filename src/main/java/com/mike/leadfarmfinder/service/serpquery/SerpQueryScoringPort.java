package com.mike.leadfarmfinder.service.serpquery;

public interface SerpQueryScoringPort {

    /**
     * Oblicza i zapisuje score dla podanego query.
     * Jeśli brak danych w discovery_run_stats — zwraca -1.
     */
    int scoreAndSave(String query);
}