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
        name = "serp_query_override",
        indexes = {
                @Index(name = "idx_serp_query_override_active", columnList = "active"),
                @Index(name = "idx_serp_query_override_original_query", columnList = "original_query")
        }
)
@Getter
@Setter
@ToString
public class SerpQueryOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_query", nullable = false, length = 500)
    private String originalQuery;

    @Column(name = "override_query", nullable = false, length = 500)
    private String overrideQuery;

    @Column(name = "original_score", nullable = false)
    private int originalScore;

    @Column(name = "tested_score", nullable = false)
    private int testedScore;

    @Column(nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}