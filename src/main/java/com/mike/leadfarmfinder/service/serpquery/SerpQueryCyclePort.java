package com.mike.leadfarmfinder.service.serpquery;

import java.util.List;

public interface SerpQueryCyclePort {

    /**
     * Uruchamia jeden cykl optymalizacji dla podanej listy queries.
     * Zwraca listę queries które zostały podmienione.
     */
    List<String> runCycle(List<String> queries);
}