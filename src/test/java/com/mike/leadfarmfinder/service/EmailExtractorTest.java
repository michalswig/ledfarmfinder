package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.service.emailextractor.DomainMxVerifier;
import com.mike.leadfarmfinder.service.emailextractor.EmailNormalizer;
import com.mike.leadfarmfinder.service.emailextractor.EmailSourceExtractor;
import com.mike.leadfarmfinder.service.emailextractor.EmailValidator;
import com.mike.leadfarmfinder.service.emailextractor.TextObfuscationNormalizer;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class EmailExtractorTest {

    @Test
    void should_return_empty_set_when_html_is_null() {
        EmailExtractor emailExtractor = new EmailExtractor(
                mock(TextObfuscationNormalizer.class),
                List.of(),
                mock(EmailNormalizer.class),
                mock(EmailValidator.class),
                mock(DomainMxVerifier.class)
        );

        Set<String> result = emailExtractor.extractEmails(null);

        assertEquals(Set.of(), result);
    }

    @Test
    void should_return_empty_set_when_html_is_blank() {
        EmailExtractor emailExtractor = new EmailExtractor(
                mock(TextObfuscationNormalizer.class),
                List.of(),
                mock(EmailNormalizer.class),
                mock(EmailValidator.class),
                mock(DomainMxVerifier.class)
        );

        Set<String> result = emailExtractor.extractEmails("   ");

        assertEquals(Set.of(), result);
    }

    @Test
    void should_normalize_html_before_passing_it_to_sources() {
        TextObfuscationNormalizer obfuscationNormalizer = mock(TextObfuscationNormalizer.class);
        EmailSourceExtractor source = mock(EmailSourceExtractor.class);
        EmailNormalizer emailNormalizer = mock(EmailNormalizer.class);
        EmailValidator emailValidator = mock(EmailValidator.class);
        DomainMxVerifier domainMxVerifier = mock(DomainMxVerifier.class);

        when(obfuscationNormalizer.normalize("kontakt: info(at)company(dot)com"))
                .thenReturn("kontakt: info@company.com");

        when(source.extractCandidates("kontakt: info@company.com"))
                .thenReturn(List.of("info@company.com"));

        when(emailNormalizer.normalizeRawCandidate("info@company.com"))
                .thenReturn("info@company.com");

        when(emailNormalizer.normalizeLocalPart("info"))
                .thenReturn("info");

        when(emailValidator.isLocalPartAllowed("info"))
                .thenReturn(true);

        when(emailValidator.isHostWithoutTldAllowed("company"))
                .thenReturn(true);

        when(emailValidator.extractKnownTld("com"))
                .thenReturn("com");

        when(domainMxVerifier.isDomainAllowed("company.com", "info@company.com"))
                .thenReturn(true);

        EmailExtractor emailExtractor = new EmailExtractor(
                obfuscationNormalizer,
                List.of(source),
                emailNormalizer,
                emailValidator,
                domainMxVerifier
        );

        Set<String> result = emailExtractor.extractEmails("kontakt: info(at)company(dot)com");

        assertEquals(Set.of("info@company.com"), result);
        verify(obfuscationNormalizer).normalize("kontakt: info(at)company(dot)com");
        verify(source).extractCandidates("kontakt: info@company.com");
    }

    @Test
    void should_return_valid_email_when_candidate_passes_full_pipeline() {
        TextObfuscationNormalizer obfuscationNormalizer = mock(TextObfuscationNormalizer.class);
        EmailSourceExtractor source = mock(EmailSourceExtractor.class);
        EmailNormalizer emailNormalizer = mock(EmailNormalizer.class);
        EmailValidator emailValidator = mock(EmailValidator.class);
        DomainMxVerifier domainMxVerifier = mock(DomainMxVerifier.class);

        when(obfuscationNormalizer.normalize("irrelevant")).thenReturn("irrelevant");
        when(source.extractCandidates("irrelevant")).thenReturn(List.of("Info@Company.com"));

        when(emailNormalizer.normalizeRawCandidate("Info@Company.com"))
                .thenReturn("Info@Company.com");
        when(emailNormalizer.normalizeLocalPart("Info"))
                .thenReturn("info");
        when(emailValidator.isLocalPartAllowed("info"))
                .thenReturn(true);
        when(emailValidator.isHostWithoutTldAllowed("Company"))
                .thenReturn(true);
        when(emailValidator.extractKnownTld("com"))
                .thenReturn("com");
        when(domainMxVerifier.isDomainAllowed("company.com", "Info@Company.com"))
                .thenReturn(true);

        EmailExtractor emailExtractor = new EmailExtractor(
                obfuscationNormalizer,
                List.of(source),
                emailNormalizer,
                emailValidator,
                domainMxVerifier
        );

        Set<String> result = emailExtractor.extractEmails("irrelevant");

        assertEquals(Set.of("info@company.com"), result);
    }

    @Test
    void should_skip_candidate_when_raw_candidate_normalization_returns_null() {
        TextObfuscationNormalizer obfuscationNormalizer = mock(TextObfuscationNormalizer.class);
        EmailSourceExtractor source = mock(EmailSourceExtractor.class);
        EmailNormalizer emailNormalizer = mock(EmailNormalizer.class);
        EmailValidator emailValidator = mock(EmailValidator.class);
        DomainMxVerifier domainMxVerifier = mock(DomainMxVerifier.class);

        when(obfuscationNormalizer.normalize("irrelevant")).thenReturn("irrelevant");
        when(source.extractCandidates("irrelevant")).thenReturn(List.of("%%%"));
        when(emailNormalizer.normalizeRawCandidate("%%%")).thenReturn(null);

        EmailExtractor emailExtractor = new EmailExtractor(
                obfuscationNormalizer,
                List.of(source),
                emailNormalizer,
                emailValidator,
                domainMxVerifier
        );

        Set<String> result = emailExtractor.extractEmails("irrelevant");

        assertEquals(Set.of(), result);
        verify(emailNormalizer).normalizeRawCandidate("%%%");
        verifyNoInteractions(emailValidator, domainMxVerifier);
    }

    @Test
    void should_skip_candidate_when_local_part_is_invalid() {
        TextObfuscationNormalizer obfuscationNormalizer = mock(TextObfuscationNormalizer.class);
        EmailSourceExtractor source = mock(EmailSourceExtractor.class);
        EmailNormalizer emailNormalizer = mock(EmailNormalizer.class);
        EmailValidator emailValidator = mock(EmailValidator.class);
        DomainMxVerifier domainMxVerifier = mock(DomainMxVerifier.class);

        when(obfuscationNormalizer.normalize("irrelevant")).thenReturn("irrelevant");
        when(source.extractCandidates("irrelevant")).thenReturn(List.of("bad@company.com"));
        when(emailNormalizer.normalizeRawCandidate("bad@company.com")).thenReturn("bad@company.com");
        when(emailNormalizer.normalizeLocalPart("bad")).thenReturn("bad");
        when(emailValidator.isLocalPartAllowed("bad")).thenReturn(false);

        EmailExtractor emailExtractor = new EmailExtractor(
                obfuscationNormalizer,
                List.of(source),
                emailNormalizer,
                emailValidator,
                domainMxVerifier
        );

        Set<String> result = emailExtractor.extractEmails("irrelevant");

        assertEquals(Set.of(), result);
        verify(emailValidator).isLocalPartAllowed("bad");
        verify(emailValidator, never()).isHostWithoutTldAllowed(anyString());
        verifyNoInteractions(domainMxVerifier);
    }

    @Test
    void should_skip_candidate_when_domain_has_invalid_mx() {
        TextObfuscationNormalizer obfuscationNormalizer = mock(TextObfuscationNormalizer.class);
        EmailSourceExtractor source = mock(EmailSourceExtractor.class);
        EmailNormalizer emailNormalizer = mock(EmailNormalizer.class);
        EmailValidator emailValidator = mock(EmailValidator.class);
        DomainMxVerifier domainMxVerifier = mock(DomainMxVerifier.class);

        when(obfuscationNormalizer.normalize("irrelevant")).thenReturn("irrelevant");
        when(source.extractCandidates("irrelevant")).thenReturn(List.of("info@company.com"));
        when(emailNormalizer.normalizeRawCandidate("info@company.com")).thenReturn("info@company.com");
        when(emailNormalizer.normalizeLocalPart("info")).thenReturn("info");
        when(emailValidator.isLocalPartAllowed("info")).thenReturn(true);
        when(emailValidator.isHostWithoutTldAllowed("company")).thenReturn(true);
        when(emailValidator.extractKnownTld("com")).thenReturn("com");
        when(domainMxVerifier.isDomainAllowed("company.com", "info@company.com")).thenReturn(false);

        EmailExtractor emailExtractor = new EmailExtractor(
                obfuscationNormalizer,
                List.of(source),
                emailNormalizer,
                emailValidator,
                domainMxVerifier
        );

        Set<String> result = emailExtractor.extractEmails("irrelevant");

        assertEquals(Set.of(), result);
        verify(domainMxVerifier).isDomainAllowed("company.com", "info@company.com");
    }

    @Test
    void should_merge_and_deduplicate_emails_from_multiple_sources_preserving_order() {
        TextObfuscationNormalizer obfuscationNormalizer = mock(TextObfuscationNormalizer.class);
        EmailSourceExtractor source1 = mock(EmailSourceExtractor.class);
        EmailSourceExtractor source2 = mock(EmailSourceExtractor.class);
        EmailNormalizer emailNormalizer = mock(EmailNormalizer.class);
        EmailValidator emailValidator = mock(EmailValidator.class);
        DomainMxVerifier domainMxVerifier = mock(DomainMxVerifier.class);

        when(obfuscationNormalizer.normalize("irrelevant")).thenReturn("irrelevant");

        when(source1.extractCandidates("irrelevant"))
                .thenReturn(List.of("info@company.com", "sales@company.com"));
        when(source2.extractCandidates("irrelevant"))
                .thenReturn(List.of("info@company.com"));

        when(emailNormalizer.normalizeRawCandidate(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(emailNormalizer.normalizeLocalPart("info")).thenReturn("info");
        when(emailNormalizer.normalizeLocalPart("sales")).thenReturn("sales");

        when(emailValidator.isLocalPartAllowed(anyString())).thenReturn(true);
        when(emailValidator.isHostWithoutTldAllowed("company")).thenReturn(true);
        when(emailValidator.extractKnownTld("com")).thenReturn("com");

        when(domainMxVerifier.isDomainAllowed(eq("company.com"), anyString())).thenReturn(true);

        EmailExtractor emailExtractor = new EmailExtractor(
                obfuscationNormalizer,
                List.of(source1, source2),
                emailNormalizer,
                emailValidator,
                domainMxVerifier
        );

        Set<String> result = emailExtractor.extractEmails("irrelevant");

        Set<String> expected = new LinkedHashSet<>(List.of("info@company.com", "sales@company.com"));
        assertEquals(expected, result);
    }
}