package com.mike.leadfarmfinder.service.outreach;

import com.mike.leadfarmfinder.entity.FarmLead;

public interface UnsubscribeUrlBuilder {
    String build(FarmLead lead);
}
