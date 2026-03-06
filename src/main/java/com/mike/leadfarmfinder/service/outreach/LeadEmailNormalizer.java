package com.mike.leadfarmfinder.service.outreach;

import com.mike.leadfarmfinder.entity.FarmLead;

public interface LeadEmailNormalizer {
    /**
     * Zwraca znormalizowany email jeśli OK.
     * Jeśli email pusty/niepoprawny -> deaktywuje lead i zwraca null.
     * Jeśli provider zablokowany (obecnie Telekom) -> zwraca null (bez deaktywacji) - zachowanie jak było.
     */
    String normalizeAndValidateOrDeactivate(FarmLead lead);
}
