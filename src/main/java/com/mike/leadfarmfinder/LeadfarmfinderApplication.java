package com.mike.leadfarmfinder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class LeadfarmfinderApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeadfarmfinderApplication.class, args);
    }

}
