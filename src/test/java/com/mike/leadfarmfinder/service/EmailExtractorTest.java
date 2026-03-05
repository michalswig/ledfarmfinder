package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.service.emailextractor.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmailExtractorTest {

    @Test
    void extract_emails_return_unique_emails() {

        TextObfuscationNormalizer obfuscation = new TextObfuscationNormalizer();

        EmailSourceExtractor fake1 = new TestEmailSourceExtractor(List.of(
                "info@company.com",
                "info@company.com",
                "sales@company.com"
        ));

        DomainMxVerifier allowAllMx = new DomainMxVerifier(null, null) {
            @Override
            public boolean isDomainAllowed(String domain, String rowEmailForLog) {
                return true;
            }
        };

        EmailNormalizer emailNormalizer = new EmailNormalizer() {
            @Override
            public String normalizeRawCandidate(String raw) {
                return raw;
            }

            @Override
            public String normalizeLocalPart(String localPart) {
                return localPart;
            }
        };

        EmailValidator emailValidator = new EmailValidator(null) {
            @Override
            public boolean isLocalPartAllowed(String localPart) {
                return true;
            }

            @Override
            public boolean isHostWithoutTldAllowed(String hostWithoutTldRaw) {
                return true;
            }

            @Override
            public String extractKnownTld(String tldPart) {
                return tldPart;
            }
        };

        EmailExtractor sut = new EmailExtractor(
                obfuscation,
                List.of(fake1),
                emailNormalizer,
                emailValidator,
                allowAllMx
        );

        //WHEN
        Set<String> result = sut.extractEmails("<html>testHtml</html>");

        //THEN
        assertEquals(Set.of("info@company.com", "sales@company.com"), result);
    }


    static class TestEmailSourceExtractor implements EmailSourceExtractor {
        private final List<String> candidates;

        TestEmailSourceExtractor(List<String> candidates) {
            this.candidates = candidates;
        }

        @Override
        public List<String> extractCandidates(String html) {
            return candidates;
        }
    }

}