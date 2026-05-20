package com.mike.leadfarmfinder.service.outreach;

import com.mike.leadfarmfinder.config.OutreachProperties;
import com.mike.leadfarmfinder.entity.FarmLead;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultLeadEligibilityPolicy implements LeadEligibilityPolicy {

    private final OutreachProperties outreachProperties;

    @Override
    public boolean isEligibleOrLog(FarmLead lead, EmailType type, LocalDateTime now) {
        if (lead == null) {
            log.warn("OutreachEligibility: lead is null, skipping");
            return false;
        }
        if (!lead.isActive()) {
            log.debug("OutreachEligibility: inactive leadId={}", lead.getId());
            return false;
        }
        if (lead.isBounce()) {
            log.debug("OutreachEligibility: bounce leadId={}", lead.getId());
            return false;
        }
        if (type == EmailType.FOLLOW_UP) {
            if (!hasFirstEmailOrLog(lead)) return false;
            if (isLastEmailTooRecent(lead, now)) return false;
        }
        return true;
    }

    private boolean hasFirstEmailOrLog(FarmLead lead) {
        if (lead.getFirstEmailSentAt() != null) return true;
        log.debug("OutreachEligibility: follow-up skipped, no first email yet. leadId={}", lead.getId());
        return false;
    }

    private boolean isLastEmailTooRecent(FarmLead lead, LocalDateTime now) {
        LocalDateTime last = lead.getLastEmailSentAt();
        if (last == null) return false;

        int minDays = outreachProperties.getFollowUpMinDaysSinceLastEmail();
        LocalDateTime cutoff = now.minusDays(minDays);

        if (last.isAfter(cutoff)) {
            log.debug("OutreachEligibility: follow-up too soon. leadId={} daysSince={}",
                    lead.getId(), Duration.between(last, now).toDays());
            return true;
        }
        return false;
    }
}