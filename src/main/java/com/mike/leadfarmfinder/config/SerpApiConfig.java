package com.mike.leadfarmfinder.config;

import com.mike.leadfarmfinder.entity.SerpApiProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SerpApiProperties.class)
public class SerpApiConfig {
}
