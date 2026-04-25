package com.mike.leadfarmfinder;

import com.mike.leadfarmfinder.config.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties({LeadFinderProperties.class,
        OutreachProperties.class,
        AgrarjobboerseProperties.class,
        EmailProperties.class,
        AwsSesProperties.class,
        LeadFinderRabbitProperties.class})
public class LeadfarmfinderApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeadfarmfinderApplication.class, args);
    }

}
