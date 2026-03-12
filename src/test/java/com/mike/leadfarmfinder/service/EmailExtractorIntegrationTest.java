package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.config.EmailExtractorProperties;
import com.mike.leadfarmfinder.service.emailextractor.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmailExtractorIntegrationTest {

    private EmailExtractor emailExtractor;

    @BeforeEach
    void setUp() {
        EmailExtractorProperties props = new EmailExtractorProperties(
                true,
                EmailExtractorProperties.MxUnknownPolicy.ALLOW,
                5000,
                Set.of("com", "de", "pl")
        );

        TextObfuscationNormalizer textObfuscationNormalizer = new TextObfuscationNormalizer();
        EmailNormalizer emailNormalizer = new EmailNormalizer();
        EmailValidator emailValidator = new EmailValidator(props);

        MxLookUp mxLookUp = domain -> switch (domain) {
            case "company.com", "example.de", "firma.pl" -> MxLookUp.MxStatus.VALID;
            case "invalid.com" -> MxLookUp.MxStatus.INVALID;
            default -> MxLookUp.MxStatus.UNKNOWN;
        };

        DomainMxVerifier domainMxVerifier = new DomainMxVerifier(mxLookUp, props);

        List<EmailSourceExtractor> sources = List.of(
                new RegexTextExtractor(),
                new MailToExtractor(textObfuscationNormalizer),
                new CloudflareCfEmailExtractor()
        );

        emailExtractor = new EmailExtractor(
                textObfuscationNormalizer,
                sources,
                emailNormalizer,
                emailValidator,
                domainMxVerifier
        );
    }

    @Test
    void should_allow_email_when_domain_mx_status_is_unknown_and_policy_is_allow() {
        Set<String> result = emailExtractor.extractEmails("Contact: info@mystery.com");

        assertEquals(Set.of("info@mystery.com"), result);
    }

    @Test
    void should_extract_email_from_cloudflare_cfemail() {
        String cfemail = encodeCfEmail("info@company.com", 0x6a);

        Set<String> result = emailExtractor.extractEmails("""
        <a href="/cdn-cgi/l/email-protection"
           class="__cf_email__"
           data-cfemail="%s">
           [email protected]
        </a>
        """.formatted(cfemail));

        assertEquals(Set.of("info@company.com"), result);
    }

    @Test
    void should_return_empty_set_when_input_is_null() {
        Set<String> result = emailExtractor.extractEmails(null);

        assertEquals(Set.of(), result);
    }

    @Test
    void should_return_empty_set_when_input_is_blank() {
        Set<String> result = emailExtractor.extractEmails("   ");

        assertEquals(Set.of(), result);
    }

    @Test
    void should_extract_plain_email_from_text() {
        Set<String> result = emailExtractor.extractEmails("Contact us: info@company.com");

        assertEquals(Set.of("info@company.com"), result);
    }

    @Test
    void should_extract_obfuscated_email_from_text() {
        Set<String> result = emailExtractor.extractEmails("Contact us: info(at)company(dot)com");

        assertEquals(Set.of("info@company.com"), result);
    }

    @Test
    void should_extract_email_from_mailto_link() {
        Set<String> result = emailExtractor.extractEmails("""
                <html>
                    <body>
                        <a href="mailto:info@company.com?subject=Hello">Email us</a>
                    </body>
                </html>
                """);

        assertEquals(Set.of("info@company.com"), result);
    }

    @Test
    void should_extract_obfuscated_email_from_mailto_link() {
        Set<String> result = emailExtractor.extractEmails("""
                <a href="mailto:info(at)company(dot)com?subject=Hello">Email us</a>
                """);

        assertEquals(Set.of("info@company.com"), result);
    }

    @Test
    void should_deduplicate_same_email_found_by_multiple_sources() {
        Set<String> result = emailExtractor.extractEmails("""
                Contact: info@company.com
                <a href="mailto:info@company.com">Write to us</a>
                """);

        assertEquals(Set.of("info@company.com"), result);
    }

    @Test
    void should_keep_insertion_order_when_multiple_distinct_emails_are_found() {
        Set<String> result = emailExtractor.extractEmails("""
                First: info@company.com
                Second: sales@company.com
                <a href="mailto:info@company.com">Mail</a>
                """);

        Set<String> expected = new LinkedHashSet<>(List.of("info@company.com", "sales@company.com"));
        assertEquals(expected, result);
    }

    @Test
    void should_skip_invalid_email() {
        Set<String> result = emailExtractor.extractEmails("Broken email: info@@company..com");

        assertEquals(Set.of(), result);
    }

    @Test
    void should_skip_email_when_domain_has_invalid_mx() {
        Set<String> result = emailExtractor.extractEmails("Contact: info@invalid.com");

        assertEquals(Set.of(), result);
    }

    @Test
    void should_skip_email_when_tld_is_not_known() {
        Set<String> result = emailExtractor.extractEmails("Contact: info@company.xyz");

        assertEquals(Set.of(), result);
    }

    private String encodeCfEmail(String email, int key) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%02x", key));
        for (char c : email.toCharArray()) {
            sb.append(String.format("%02x", ((int) c) ^ key));
        }
        return sb.toString();
    }
}
