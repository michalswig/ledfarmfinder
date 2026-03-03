package com.mike.leadfarmfinder.service.outreach;

public interface MailSenderGateway {
    SendResult send(PreparedMail mail);
}
