package com.mike.leadfarmfinder.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "serp_query_history",
        indexes = {
                @Index(name = "idx_serp_query_history_replaced_query", columnList = "replaced_query"),
                @Index(name = "idx_serp_query_history_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@ToString
public class SerpQueryHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "replaced_query", nullable = false, updatable = false, length = 500)
    private String replacedQuery;

    @Column(name = "new_query", nullable = false, updatable = false, length = 500)
    private String newQuery;

    @Column(name = "replaced_query_score", nullable = false, updatable = false)
    private int replacedQueryScore;

    @Column(name = "new_query_score", nullable = false, updatable = false)
    private int newQueryScore;

    @Column(name = "ai_reason", updatable = false, length = 1000)
    private String aiReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
