package com.mike.leadfarmfinder.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "discovered_urls",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_discovered_urls_url",
                        columnNames = "url"
                )
        },
        indexes = {
                @Index(
                        name = "idx_discovered_urls_first_seen_at",
                        columnList = "first_seen_at"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscoveredUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "url", nullable = false, length = 1000)
    private String url;

    @Column(name = "is_farm", nullable = false)
    private boolean farm;

    @Column(name = "is_seasonal_jobs", nullable = false)
    private boolean seasonalJobs;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;
}
