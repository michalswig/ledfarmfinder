package com.mike.leadfarmfinder.service;

import com.mike.leadfarmfinder.config.EmailExtractorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class EmailExtractorTest {

//    private MxLookUp mxLookUp;
//    private EmailExtractorProperties props;
//    private EmailExtractor emailExtractor;
//
//    @BeforeEach
//    void setUp() {
//        mxLookUp = mock(MxLookUp.class);
//        props = new EmailExtractorProperties(
//                false,
//                "WARN",
//                2000,
//                Set.of("de", "com")
//        );
//        emailExtractor = new EmailExtractor(mxLookUp, props);
//    }
//
//    @Nested
//    @DisplayName("normalizeObfuscatedEmailsInText")
//    class NormalizeObfuscatedEmailsInText {
//
//        @Test
//        @DisplayName("null -> null")
//        void shouldReturnNull_whenInputIsNull() {
//            // Arrange
//            String input = null;
//
//            // Act
//            String result = emailExtractor.normalizeObfuscatedEmailsInText(input);
//
//            // Assert
//            assertNull(result);
//        }
//
//        @Test
//        @DisplayName("blank -> unchanged")
//        void shouldReturnInputUnchanged_whenInputIsBlank() {
//            // Arrange
//            String input = " ";
//
//            // Act
//            String result = emailExtractor.normalizeObfuscatedEmailsInText(input);
//
//            // Assert
//            assertEquals(" ", result);
//        }
//
//        @Test
//        @DisplayName("(at) and (dot) -> @ and .")
//        void shouldReplaceParenthesizedAtAndDot_whenObfuscatedEmailUsesParentheses() {
//            // Arrange
//            String input = "kontakt: info(at)example(dot)de";
//
//            // Act
//            String result = emailExtractor.normalizeObfuscatedEmailsInText(input);
//
//            // Assert
//            assertEquals("kontakt: info@example.de", result);
//        }
//
//        @Test
//        @DisplayName("( at ) and ( dot ) with spaces -> @ and .")
//        void shouldReplaceParenthesizedAtAndDot_whenParenthesesContainSpaces() {
//            // Arrange
//            String input = "kontakt: info ( at ) example ( dot ) de";
//
//            // Act
//            String result = emailExtractor.normalizeObfuscatedEmailsInText(input);
//
//            // Assert
//            assertEquals("kontakt: info@example.de", result);
//        }
//
//        @Test
//        @DisplayName("at and dot -> @ and .")
//        void shouldReplaceAtAndDotWords_whenObfuscationUsesWords() {
//            // Arrange
//            String input = "kontakt: info at example dot de";
//
//            // Act
//            String result = emailExtractor.normalizeObfuscatedEmailsInText(input);
//
//            // Assert
//            assertEquals("kontakt: info@example.de", result);
//        }
//
//        @Test
//        @DisplayName("case insensitive")
//        void shouldReplaceAtAndDotWords_caseInsensitive() {
//            // Arrange
//            String input = "KONTAKT: INFO AT EXAMPLE DOT DE";
//
//            // Act
//            String result = emailExtractor.normalizeObfuscatedEmailsInText(input);
//
//            // Assert
//            assertEquals("KONTAKT: INFO@EXAMPLE.DE", result);
//        }
//
//        @Test
//        @DisplayName("should not change normal email")
//        void shouldNotModifyText_whenEmailIsAlreadyNormal() {
//            // Arrange
//            String input = "kontakt: info@example.de";
//
//            // Act
//            String result = emailExtractor.normalizeObfuscatedEmailsInText(input);
//
//            // Assert
//            assertEquals("kontakt: info@example.de", result);
//        }
//    }
//
//    @Nested
//    @DisplayName("normalizeEmail")
//    class NormalizeEmail {
//
//        @Test
//        @DisplayName("null -> null")
//        void shouldReturnNull_whenEmailIsNull() {
//            // Arrange
//            String input = null;
//
//            // Act
//            String result = emailExtractor.normalizeEmail(input);
//
//            // Assert
//            assertNull(result);
//        }
//
//        @Test
//        @DisplayName("blank after trim -> null")
//        void shouldReturnNull_whenEmailIsBlankAfterTrim() {
//            // Arrange
//            String input = " ";
//
//            // Act
//            String result = emailExtractor.normalizeEmail(input);
//
//            // Assert
//            assertNull(result);
//        }
//
//        @Test
//        @DisplayName("trailing junk -> valid email")
//        void shouldStripWrappingCharacters_whenEmailIsSurroundedByAngleBrackets() {
//            // Arrange
//            String input = "<email@domain.com>";
//
//            // Act
//            String result = emailExtractor.normalizeEmail(input);
//
//            // Assert
//            assertEquals("email@domain.com", result);
//        }
//
//        @Test
//        @DisplayName("leading %xx is removed -> valid email")
//        void shouldUrlDecodeAndStripLeadingGarbage_whenEmailStartsWithPercentEncoding() {
//            // Arrange
//            String input = "%3Cinfo@example.de";
//
//            // Act
//            String result = emailExtractor.normalizeEmail(input);
//
//            // Assert
//            assertEquals("info@example.de", result);
//        }
//
//        @Test
//        @DisplayName("first char must be alfanum: starts with '*' -> null")
//        void shouldReturnNull_whenEmailStartsWithNonAlphanumeric() {
//            assertNull(emailExtractor.normalizeEmail("*info@example.com"));
//        }
//
//        @Test
//        @DisplayName("atIndex <= 0: '@' at beginning -> null")
//        void shouldReturnNull_whenAtSignIsFirstCharacter() {
//            assertNull(emailExtractor.normalizeEmail("@example.de"));
//        }
//
//        @Test
//        @DisplayName("missing dot after '@': info@example -> null")
//        void shouldReturnNull_whenDomainHasNoDotAfterAt() {
//            assertNull(emailExtractor.normalizeEmail("info@example"));
//        }
//
//        @Test
//        @DisplayName("dot before '@': info.example@de -> null")
//        void shouldReturnNull_whenDotAppearsBeforeAtSign() {
//            assertNull(emailExtractor.normalizeEmail("info.example@de"));
//        }
//
//        @Test
//        @DisplayName("second '@' -> null")
//        void shouldReturnNull_whenEmailContainsMultipleAtSigns() {
//            assertNull(emailExtractor.normalizeEmail("examp@le@dot.com"));
//        }
//
//        @Test
//        @DisplayName("splits + validates -> valid email")
//        void shouldLowercaseAndValidate_whenEmailIsValid() {
//            String result = emailExtractor.normalizeEmail("INFO@Example.com");
//            assertEquals("info@example.com", result);
//        }
//
//        @Test
//        @DisplayName("splits + validates glued phone digits -> valid email")
//        void shouldExtractEmail_whenPhoneDigitsAreGluedBeforeLocalPart() {
//            String result = emailExtractor.normalizeEmail("0176123456mona@example.de");
//            assertEquals("mona@example.de", result);
//        }
//
//        @Test
//        @DisplayName("host with subdomains is accepted")
//        void shouldAcceptSubdomains_whenDomainContainsMultipleLabels() {
//            String result = emailExtractor.normalizeEmail("john@mail.sub.example.de");
//            assertEquals("john@mail.sub.example.de", result);
//        }
//
//        @Test
//        @DisplayName("MX check enabled + VALID -> valid email")
//        void shouldReturnEmail_whenMxCheckEnabledAndDomainMxIsValid() {
//            // Arrange
//            EmailExtractorProperties mxOn = new EmailExtractorProperties(true, "WARN", 2000, Set.of("de"));
//            EmailExtractor sut = new EmailExtractor(mxLookUp, mxOn);
//            when(mxLookUp.checkDomain("example.de")).thenReturn(MxLookUp.MxStatus.VALID);
//
//            // Act
//            String result = sut.normalizeEmail("INFO@EXAMPLE.DE");
//
//            // Assert
//            assertEquals("info@example.de", result);
//            verify(mxLookUp).checkDomain("example.de");
//        }
//    }
//
//    @Nested
//    @DisplayName("extractKnownTld")
//    class ExtractKnownTld {
//
//        @Test
//        @DisplayName("empty tldPart (email ends with '.') -> null")
//        void shouldReturnNull_whenTldPartIsEmpty() {
//            assertNull(emailExtractor.normalizeEmail("info@example."));
//        }
//
//        @Test
//        @DisplayName("tld case-insensitive: DE -> de")
//        void shouldNormalizeTldToLowercase_whenTldIsUppercase() {
//            String result = emailExtractor.normalizeEmail("info@example.DE");
//            assertEquals("info@example.de", result);
//        }
//
//        @Test
//        @DisplayName("unknown TLD -> null")
//        void shouldReturnNull_whenTldIsUnknown() {
//            assertNull(emailExtractor.normalizeEmail("info@example.xyz"));
//        }
//    }
//
//    @Nested
//    @DisplayName("isLocalPartAllowed")
//    class IsLocalPartAllowed {
//
//        @Test
//        @DisplayName("local-part too short (1) -> null")
//        void shouldReturnNull_whenLocalPartTooShort() {
//            assertNull(emailExtractor.normalizeEmail("a@example.de"));
//        }
//
//        @Test
//        @DisplayName("local-part too long (>40) -> null")
//        void shouldReturnNull_whenLocalPartTooLong() {
//            String longLocal = "a".repeat(41);
//            assertNull(emailExtractor.normalizeEmail(longLocal + "@example.de"));
//        }
//
//        @Test
//        @DisplayName("local-part illegal char '!' -> null")
//        void shouldReturnNull_whenLocalPartContainsIllegalCharacter() {
//            assertNull(emailExtractor.normalizeEmail("ab!cd@example.de"));
//        }
//
//        @Test
//        @DisplayName("local-part unicode u00fc -> null")
//        void shouldReturnNull_whenLocalPartLooksLikeUnicodeEscapeSequence() {
//            assertNull(emailExtractor.normalizeEmail("u00fcmail@example.de"));
//        }
//    }
//
//    @Nested
//    @DisplayName("isHostWithoutTldAllowed")
//    class IsHostWithoutTldAllowed {
//
//        @Test
//        @DisplayName("host extra space -> null")
//        void shouldReturnNull_whenHostContainsSpace() {
//            assertNull(emailExtractor.normalizeEmail("info@ex ample.de"));
//        }
//
//        @Test
//        @DisplayName("host contains '_' -> null")
//        void shouldReturnNull_whenHostContainsUnderscore() {
//            assertNull(emailExtractor.normalizeEmail("info@ex_ample.de"));
//        }
//
//        @Test
//        @DisplayName("host starts with '.' -> null")
//        void shouldReturnNull_whenHostStartsWithDot() {
//            assertNull(emailExtractor.normalizeEmail("info@.example.de"));
//        }
//
//        @Test
//        @DisplayName("host starts with '-' -> null")
//        void shouldReturnNull_whenHostStartsWithDash() {
//            assertNull(emailExtractor.normalizeEmail("info@-example.de"));
//        }
//
//        @Test
//        @DisplayName("host ends with '-' -> null")
//        void shouldReturnNull_whenHostEndsWithDashBeforeTld() {
//            assertNull(emailExtractor.normalizeEmail("info@example-.de"));
//        }
//
//        @Test
//        @DisplayName("host contains '..' -> null")
//        void shouldReturnNull_whenHostContainsDoubleDot() {
//            assertNull(emailExtractor.normalizeEmail("info@ex..ample.de"));
//        }
//
//        @Test
//        @DisplayName("host with subdomain and dash -> valid")
//        void shouldAcceptHost_whenContainsSubdomainAndDash() {
//            String result = emailExtractor.normalizeEmail("info@mail-1.sub.example.de");
//            assertEquals("info@mail-1.sub.example.de", result);
//        }
//    }
}