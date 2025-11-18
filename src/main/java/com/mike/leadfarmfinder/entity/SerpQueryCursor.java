package com.mike.leadfarmfinder.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "serp_query_cursor")
@Getter
@Setter
@ToString
public class SerpQueryCursor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Zapytanie używane w SerpAPI (np. "Saisonarbeit Erdbeeren Hof Niedersachsen").
     * Unikalne – dla każdego query mamy jeden kursor.
     */
    @Column(nullable = false, unique = true, length = 500)
    private String query;

    /**
     * Strona SERP, od której zaczniesz przy następnym runie.
     */
    @Column(name = "current_page", nullable = false)
    private int currentPage;

    /**
     * Maksymalna strona SERP, do której schodzisz zanim zawiniesz się do 1.
     */
    @Column(name = "max_page", nullable = false)
    private int maxPage;

    /**
     * Ostatni czas, kiedy ten kursor był użyty.
     */
    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;
}
