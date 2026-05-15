package com.mike.leadfarmfinder.service.discovery;

import java.util.List;

public interface DiscoveryQueryProvider {

    /**
     * Zwraca aktualną listę queries do użycia w discovery.
     * Może być to lista z YAMLa, z DB, lub kombinacja obu.
     */
    List<String> getQueries();
}