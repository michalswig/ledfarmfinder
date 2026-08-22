package com.mike.leadfarmfinder.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Warianty treści maili outreach, używane do rotacji szablonów.
 * <p>
 * Cel: redukcja "content fingerprinting" po stronie filtrów antyspamowych
 * (np. Strato "Refused by local policy") — identyczna treść wysyłana
 * masowo do wielu odbiorców jest łatwiejsza do wykrycia niż rotowana.
 * <p>
 * Jeśli listy są puste, {@link com.mike.leadfarmfinder.service.outreach.DefaultMailComposer}
 * spada z powrotem na pojedyncze, statyczne szablony z {@link OutreachProperties}
 * — więc wdrożenie tej klasy nie wymaga natychmiastowej zmiany configu.
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "leadfinder.outreach.templates")
public class EmailTemplateVariants {

    /** Warianty treści pierwszego maila. */
    private List<String> first = List.of();

    /** Warianty treści maila follow-up. */
    private List<String> followUp = List.of();
}