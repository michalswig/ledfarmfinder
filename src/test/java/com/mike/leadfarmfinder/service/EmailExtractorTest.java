package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.config.EmailExtractorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class EmailExtractorTest {

    private MxLookUp mxLookUp;
    private EmailExtractorProperties props;
    private EmailExtractor emailExtractor;

    @BeforeEach
    void setUp() {
        mxLookUp = mock(MxLookUp.class);
        props = new EmailExtractorProperties(
                false,
                "WARN",    // mxUnknownPolicy
                2000       // mxTimeoutMs
        );
        emailExtractor = new EmailExtractor(mxLookUp, props);
    }

    @Nested
    @DisplayName("normalizeObfuscatedEmailsInText")
    class NormalizeObfuscatedEmailsInText {
        @Test
        @DisplayName("null -> null")
        void normalize_when_null_returns_null() {
            //Arrange
            String input = null;
            //Act
            String result = emailExtractor.normalizeObfuscatedEmailsInText(input);
            //Assert
            assertNull(result);
        }

        @Test
        @DisplayName("blank -> unchanged")
        void normalize_when_blank_returns_unchanged() {
            //Arrange
            String input = " ";
            //Act
            String result = emailExtractor.normalizeObfuscatedEmailsInText(input);
            //Assert
            assertEquals(" ", result);
        }

        @Test
        @DisplayName("(at) and (dot) -> @ and .")
        void normalize_parentheses_at_dot() {
            //Arrange
            String input = "kontakt: info(at)example(dot)de";
            //Act
            String result = emailExtractor.normalizeObfuscatedEmailsInText(input);
            //Assert
            assertEquals("kontakt: info@example.de", result);
        }

        @Test
        @DisplayName("( at ) and ( dot ) with spaces -> @ and .")
        void normalize_parentheses_at_dot_with_spaces() {
            //Arrange
            String input = "kontakt: info ( at ) example ( dot ) de";
            //Act
            String result = emailExtractor.normalizeObfuscatedEmailsInText(input);
            //Assert
            assertEquals("kontakt: info@example.de", result);
        }

        @Test
        @DisplayName("at and dot -> @ and .")
        void normalize_parentheses_at_dot_words() {
            //Arrange
            String input = "kontakt: info at example dot de";
            //Act
            String result = emailExtractor.normalizeObfuscatedEmailsInText(input);
            //Assert
            assertEquals("kontakt: info@example.de", result);
        }

        @Test
        @DisplayName("case sensitive")
        void normalize_case_sensitive() {
            //Arrange
            String input = "KONTAKT: INFO AT EXAMPLE DOT DE";
            //Act
            String result = emailExtractor.normalizeObfuscatedEmailsInText(input);
            //Assert
            assertEquals("KONTAKT: INFO@EXAMPLE.DE", result);
        }

        @Test
        @DisplayName("should not change normal email")
        void normalize_should_not_change_normal_email() {
            //Arrange
            String input = "kontakt: info@example.de";
            //Act
            String result = emailExtractor.normalizeObfuscatedEmailsInText(input);
            //Assert
            assertEquals("kontakt: info@example.de", result);
        }
    }

    @Nested
    @DisplayName("normalizeEmail")
    class NormalizeEmail {

        @Test
        @DisplayName("null -> null")
        void normalize_when_null_returns_null() {
            //Arrange
            String input = null;
            //Act
            String result = emailExtractor.normalizeEmail(input);
            //Assert
            assertNull(result);
        }

        @Test
        @DisplayName("blank after trim -> null")
        void normalize_when_empty_returns_null() {
            //Arrange
            String input = " ";
            //Act
            String result = emailExtractor.normalizeEmail(input);
            //Assert
            assertNull(result);
        }

        @Test
        @DisplayName("trailing junk -> valid email")
        void normalize_when_trailing_junk_returns_valid_email() {
            //Arrange
            String input = "<email@domain.com>";
            //Act
            String result = emailExtractor.normalizeEmail(input);
            //Assert
            assertEquals("email@domain.com", result);
        }

        @Test
        @DisplayName("leading %xx is removed -> valid email")
        void normalize_when_leading_returns_valid_email() {
            //Arrange
            String result = emailExtractor.normalizeEmail("%3Cinfo@example.de");
            //Act Assert
            assertEquals("info@example.de", result);
        }

        @Test
        @DisplayName("first char must be alfanum: starts with '*' -> null")
        void normalize_when_first_char_not_alfanum_returns_null() {
            assertNull(emailExtractor.normalizeEmail("*info@example.com"));
        }

        @Test
        @DisplayName("atIndex <= 0: '@' at beginning -> null")
        void atAtBeginning_returnsNull() {
            assertNull(emailExtractor.normalizeEmail("@example.de"));
        }

        @Test
        @DisplayName("missing dot after '@': info@example -> null")
        void missingDotAfterAt_returnsNull() {
            assertNull(emailExtractor.normalizeEmail("info@example"));
        }

        @Test
        @DisplayName("dot before '@': info.example@de -> null")
        void dotBeforeAt_returnsNull() {
            assertNull(emailExtractor.normalizeEmail("info.example@de"));
        }

        @Test
        @DisplayName("second '@' -> null")
        void doubleAt_returnsNull() {
            assertNull(emailExtractor.normalizeEmail("examp@le@dot.com"));
        }

        @Test
        @DisplayName("splits + validates -> valid email")
        void splits_when_validates_returns_valid_email() {
            String result = emailExtractor.normalizeEmail("INFO@Example.com");
            assertEquals("info@example.com", result);

        }

        @Test
        @DisplayName("splits + validates glued phone digits -> valid email")
        void splits_when_validates_phoneFix_returns_valid_email() {
            String result = emailExtractor.normalizeEmail("0176123456mona@example.de");
            assertEquals("mona@example.de", result);
        }

        @Test
        @DisplayName("host with subdomains is accepted")
        void splits_when_validates_subdomain_returns_valid_email() {
            String result = emailExtractor.normalizeEmail("john@mail.sub.example.de");
            assertEquals("john@mail.sub.example.de", result);
        }

        @Test
        @DisplayName("MX check enabled + VALID -> valid email")
        void mxCheck_enabled_valid_email() {
            // Arrange
            EmailExtractorProperties mxOn = new EmailExtractorProperties(true, "WARN", 2000);
            EmailExtractor sut = new EmailExtractor(mxLookUp, mxOn);
            when(mxLookUp.checkDomain("example.de")).thenReturn(MxLookUp.MxStatus.VALID);

            // Act
            String result = sut.normalizeEmail("INFO@EXAMPLE.DE");

            // Assert
            assertEquals("info@example.de", result);
            verify(mxLookUp).checkDomain("example.de");
        }
    }

    @Nested
    @DisplayName("extractKnownTld")
    class extractKnownTld {

        @Test
        @DisplayName("empty tldPart (email ends with '.') -> null")
        void tldPart_empty_returnsNull() {
            assertNull(emailExtractor.normalizeEmail("info@example."));
        }

        @Test
        @DisplayName("tld case-insensitive: DE -> de")
        void tld_caseInsensitive_match() {
            String result = emailExtractor.normalizeEmail("info@example.DE");
            assertEquals("info@example.de", result);
        }

        @Test
        @DisplayName("unknown TLD -> null")
        void unknownTld_returnsNull() {
            assertNull(emailExtractor.normalizeEmail("info@example.xyz"));
        }
    }

    @Nested
    @DisplayName("isLocalPartAllowed")
    class isLocalPartAllowed {

        @Test
        @DisplayName("local-part too short (1) -> null")
        void localPart_tooShort_returnsNull() {
            assertNull(emailExtractor.normalizeEmail("a@example.de"));
        }

        @Test
        @DisplayName("local-part too long (>40) -> null")
        void localPart_tooLong_returnsNull() {
            String longLocal = "a".repeat(41);
            assertNull(emailExtractor.normalizeEmail(longLocal + "@example.de"));
        }

        @Test
        @DisplayName("local-part illegal char '!' -> null")
        void localPart_illegalChar_returnsNull() {
            assertNull(emailExtractor.normalizeEmail("ab!cd@example.de"));
        }

        @Test
        @DisplayName("local-part unicode u00fc -> null")
        void localPart_suspiciousUnicodeEscape_returnsNull() {
            assertNull(emailExtractor.normalizeEmail("u00fcmail@example.de"));
        }
    }

    @Nested
    @DisplayName("isHostWithoutTldAllowed")
    class isHostWithoutTldAllowed {

        @Test
        @DisplayName("host extra space -> null")
        void host_containsSpace_returnsNull() {
            assertNull(emailExtractor.normalizeEmail("info@ex ample.de"));
        }

        @Test
        @DisplayName("host contains '_' -> null")
        void host_containsUnderscore_returnsNull() {
            assertNull(emailExtractor.normalizeEmail("info@ex_ample.de"));
        }

        @Test
        @DisplayName("host starts with '.' -> null")
        void host_startsWithDot_returnsNull() {
            assertNull(emailExtractor.normalizeEmail("info@.example.de"));
        }

        @Test
        @DisplayName("host starts with '-' -> null")
        void host_startsWithDash_returnsNull() {
            assertNull(emailExtractor.normalizeEmail("info@-example.de"));
        }

        @Test
        @DisplayName("host ends with '-' -> null")
        void host_endsWithDash_returnsNull() {
            assertNull(emailExtractor.normalizeEmail("info@example-.de"));
        }

        @Test
        @DisplayName("host contains '..' -> null")
        void host_doubleDot_returnsNull() {
            assertNull(emailExtractor.normalizeEmail("info@ex..ample.de"));
        }

        @Test
        @DisplayName("host with subdomain and dash -> valid")
        void host_valid_subdomainAndDash() {
            String result = emailExtractor.normalizeEmail("info@mail-1.sub.example.de");
            assertEquals("info@mail-1.sub.example.de", result);
        }
    }

}