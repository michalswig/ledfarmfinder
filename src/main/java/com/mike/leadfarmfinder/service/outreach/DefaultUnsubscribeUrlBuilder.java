package com.mike.leadfarmfinder.service.outreach;

import com.mike.leadfarmfinder.entity.FarmLead;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultUnsubscribeUrlBuilder implements UnsubscribeUrlBuilder {

    @Value("${app.outreach.unsubscribe-base-url:}")
    private String unsubscribeBaseUrl;

    @Override
    public String build(FarmLead lead) {
        if (unsubscribeBaseUrl == null || unsubscribeBaseUrl.isBlank()) return "";
        if (lead == null) return "";
        if (lead.getUnsubscribeToken() == null || lead.getUnsubscribeToken().isBlank()) return "";
        return unsubscribeBaseUrl + lead.getUnsubscribeToken();
    }
}
