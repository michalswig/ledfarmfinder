package com.mike.leadfarmfinder.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "serp_query_score",
        indexes = {
                @Index(name = "idx_serp_query_score_score", columnList = "score")
        }
)
@Getter
@Setter
@ToString
public class SerpQueryScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true,  length = 500)
    private String query;

    @Column(nullable = false)
    private int score;

    @Column(name = "accepted_urls", nullable = false)
    private int acceptedUrls;

    @Column(name = "rejected_urls", nullable = false)
    private int rejectedUrls;

    @Column(name = "pages_visited", nullable = false)
    private int pagesVisited;

    @Column(name = "runs_count", nullable = false)
    private int runsCount;

    @Column(name = "last_evaluated_at")
    private LocalDateTime lastEvaluatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

}
