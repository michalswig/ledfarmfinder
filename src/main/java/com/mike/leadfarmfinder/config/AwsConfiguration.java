package com.mike.leadfarmfinder.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

@Configuration
@EnableConfigurationProperties(AwsSesProperties.class)
public class AwsConfiguration {

    @Bean
    public SesV2Client sesV2Client(AwsSesProperties awsSesProperties) {
        return SesV2Client.builder()
                .region(Region.of(awsSesProperties.region()))
                .build();
    }
}