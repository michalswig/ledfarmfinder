package com.mike.leadfarmfinder.service.outreach.event;

public enum MailBounceCategory {
    HARD_BOUNCE,
    SOFT_BOUNCE,
    DNS_FAILURE,
    MAILBOX_NOT_FOUND,
    DOMAIN_NOT_FOUND,
    SPAM_BLOCK,
    UNKNOWN
}
