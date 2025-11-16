package com.mike.leadfarmfinder.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "farm_leads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FarmLead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    private String sourceUrl;

    private LocalDateTime createdAt;

    private boolean active;

    private String unsubscribeToken;
}
