package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.config.EmailProperties;
import com.mike.leadfarmfinder.service.emailextractor.*;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = EmailExtractorIntegrationTest.TestConfig.class)
class EmailExtractorIntegrationTest {

    @Resource
    private EmailExtractor emailExtractor;

    @Test
    void shouldExtractOnlyValidNormalizedEmails() {
        String html = """
                Kontakt:
                sales (at) firma (dot) de
                info@example.com,
                broken@@example.com
                bad@invalid.com
                %20office@example.com
                """;

        Set<String> result = emailExtractor.extractEmails(html);

        assertEquals(Set.of(
                "sales@firma.de",
                "info@example.com",
                "office@example.com"
        ), result);
    }

    @Configuration
    @Import({
            EmailExtractor.class,
            TextObfuscationNormalizer.class,
            EmailNormalizer.class,
            EmailValidator.class,
            DomainMxVerifier.class
    })
    static class TestConfig {

        @Bean
        EmailProperties emailProperties() {
            return new EmailProperties(
                    "ses",
                    true,
                    EmailProperties.MxUnknownPolicy.ALLOW,
                    5000L,
                    Set.of("com", "de", "pl")
            );
        }

        @Bean
        MxLookUp mxLookUp() {
            return domain -> switch (domain) {
                case "firma.de", "example.com", "example.de", "firma.pl" -> MxLookUp.MxStatus.VALID;
                case "invalid.com" -> MxLookUp.MxStatus.INVALID;
                default -> MxLookUp.MxStatus.UNKNOWN;
            };
        }

        @Bean
        EmailSourceExtractor extractorOne() {
            return html -> List.of(
                    "sales@firma.de",
                    "info@example.com,",
                    "broken@@example.com",
                    "bad@invalid.com",
                    "%20office@example.com"
            );
        }

        @Bean
        EmailSourceExtractor extractorTwo() {
            return html -> List.of(
                    "sales (at) firma (dot) de"
            );
        }
    }
}