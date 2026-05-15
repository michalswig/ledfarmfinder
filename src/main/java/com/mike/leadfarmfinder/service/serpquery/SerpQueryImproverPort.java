package com.mike.leadfarmfinder.service.serpquery;

import java.util.List;

public interface SerpQueryImproverPort {

    /**
     * Dla podanego słabego query generuje listę propozycji lepszych queries.
     * Zwraca pustą listę gdy OpenAI nie odpowie lub odpowiedź będzie nieparsowalna.
     */
    List<String> suggestImprovements(String weakQuery, int score);
}