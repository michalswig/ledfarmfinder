package com.mike.leadfarmfinder.config;

import com.mike.leadfarmfinder.service.serpquery.SerpQueryImproverPromptBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SerpQueryConfig {

    @Bean
    public SerpQueryImproverPromptBuilder serpQueryImproverPromptBuilder() {
        return new SerpQueryImproverPromptBuilder();
    }
}