package com.mike.leadfarmfinder.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "discovery_run_stats")
@Getter
@Setter
@ToString
public class DiscoveryRunStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Zapytanie użyte w SERP (np. "Saisonarbeit Erdbeeren Hof Niedersachsen").
     */
    @Column(nullable = false, length = 500)
    private String query;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "start_page", nullable = false)
    private int startPage;

    @Column(name = "end_page", nullable = false)
    private int endPage;

    @Column(name = "pages_visited", nullable = false)
    private int pagesVisited;

    @Column(name = "raw_urls", nullable = false)
    private int rawUrls;

    @Column(name = "cleaned_urls", nullable = false)
    private int cleanedUrls;

    @Column(name = "accepted_urls", nullable = false)
    private int acceptedUrls;

    @Column(name = "rejected_urls", nullable = false)
    private int rejectedUrls;

    @Column(name = "errors", nullable = false)
    private int errors;

    @Column(name = "filtered_already_discovered", nullable = false)
    private Integer filteredAlreadyDiscovered = 0;

}