package com.mike.leadfarmfinder.service.outreach;

import com.mike.leadfarmfinder.entity.FarmLead;

public interface MailComposer {
    PreparedMail compose(FarmLead lead, String normalizeTo, EmailType type);
}
