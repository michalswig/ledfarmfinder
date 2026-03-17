package com.mike.leadfarmfinder.service.outreach;

import com.mike.leadfarmfinder.entity.FarmLead;

import java.time.LocalDateTime;

public interface LeadEligibilityPolicy {

    /**
     * Zwraca true jeśli lead może dostać email danego typu.
     * Loguje powód odrzucenia (jak było w OutreachService).
     */
    boolean isEligibleOrLog(FarmLead lead, EmailType type, LocalDateTime now);
}
