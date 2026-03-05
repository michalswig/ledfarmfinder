package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.config.EmailExtractorProperties;
import com.mike.leadfarmfinder.service.emailextractor.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringJUnitConfig
@Import({EmailExtractor.class, ModularEmailExtractorTest.Config.class})
class ModularEmailExtractorTest {

    @Autowired
    private EmailExtractor emailExtractor;

    @TestConfiguration
    static class Config {
        @Bean
        EmailExtractorProperties emailExtractorProperties() {
            return new EmailExtractorProperties(
                    true,
                    EmailExtractorProperties.MxUnknownPolicy.ALLOW,
                    5000,
                    Set.of("com")
            );
        }

        @Bean
        TextObfuscationNormalizer obfuscationNormalizer() {
            return new TextObfuscationNormalizer();
        }

        @Bean
        List<EmailSourceExtractor> sources() {
            return List.of(html -> List.of("info@company.com"));
        }

        @Bean
        EmailNormalizer emailNormalizer() {
            return new EmailNormalizer();
        }

        @Bean
        EmailValidator emailValidator(EmailExtractorProperties props) {
            return new EmailValidator(props);
        }

//        @Bean
//        EmailValidator emailValidator() {
//            return new EmailValidator(null) {
//                @Override public boolean isLocalPartAllowed(String localPart) { return true; }
//                @Override public boolean isHostWithoutTldAllowed(String hostWithoutTldRaw) { return true; }
//                @Override public String extractKnownTld(String tldPart) { return "de"; }
//            };
//        }

        @Bean
        MxLookUp mxLookUp(EmailExtractorProperties props) {
            return domain -> MxLookUp.MxStatus.VALID;
        }

        //    @Bean
//    DomainMxVerifier domainMxVerifier() {
//        return new DomainMxVerifier(null, null) {
//            @Override
//            public boolean isDomainAllowed(String domain, String rowEmailForLog) {
//                return true;
//            }
//        };
//    }

        @Bean
        DomainMxVerifier domainMxVerifier(MxLookUp mxLookUp, EmailExtractorProperties props) {
            return new DomainMxVerifier(mxLookUp, props);
        }

    }

    @Test
    void should_extract_emails() {
        //Given
        Set<String> result = emailExtractor.extractEmails("contact info@company.com");
        //When Then
        assertEquals(Set.of("info@company.com"), result);
    }


}