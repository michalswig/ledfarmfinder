package com.mike.leadfarmfinder.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "leadfinder.rabbit")
public class LeadFinderRabbitProperties {
    private String outreachEventsExchange;
    private String outreachEventsQueue;
    private String outreachEventsDlq;
    private String outreachEventsRoutingKey;
}
