package com.mike.leadfarmfinder.entity;

import com.mike.leadfarmfinder.util.TokenGenerator;
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

    // Uwaga: DB ma unique, ale to będzie case-sensitive. Jeśli chcesz case-insensitive, robimy oddzielnie (CITEXT / lower index).
    @Column(nullable = false, unique = true, updatable = false)
    private String email;

    private String sourceUrl;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private boolean active;

    @Column(unique = true)
    private String unsubscribeToken;

    private LocalDateTime firstEmailSentAt;
    private LocalDateTime lastEmailSentAt;

    @Column(nullable = false)
    private boolean bounce;

    @Column(name = "last_delivery_status")
    private String lastDeliveryStatus;

    @Column(name = "bounce_type")
    private String bounceType;

    @Column(name = "bounce_reason", length = 1000)
    private String bounceReason;

    @Column(name = "last_bounce_at")
    private LocalDateTime lastBounceAt;

    @Column(name = "last_delivery_event_at")
    private LocalDateTime lastDeliveryEventAt;

    @Column(name = "delivery_provider_message_id")
    private String deliveryProviderMessageId;

    @Column(name = "review_required")
    private boolean reviewRequired;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();

        // domyślnie aktywny
        // (boolean domyślnie false, więc ustawiamy true jeśli ktoś nie ustawił w builderze)
        if (!active) active = true;

        // bounce domyślnie false (zostaje)
        // unsubscribe zawsze ma istnieć
        if (unsubscribeToken == null || unsubscribeToken.isBlank()) {
            unsubscribeToken = TokenGenerator.generateShortToken();
        }
    }
}
