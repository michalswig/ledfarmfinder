package com.mike.leadfarmfinder;

import com.mike.leadfarmfinder.config.*;
import com.mike.leadfarmfinder.service.directory.DirectoryProperties;
import com.mike.leadfarmfinder.service.directory.HofladenFinderProperties;
import com.mike.leadfarmfinder.service.osm.OsmProperties;
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
        LeadFinderRabbitProperties.class,
        HofladenFinderProperties.class,
        DirectoryProperties.class,
        OsmProperties.class})
public class LeadfarmfinderApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeadfarmfinderApplication.class, args);
    }

}
