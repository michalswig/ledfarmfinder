package com.mike.leadfarmfinder.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
class EmailExtractorTest {

    @Mock MxLookUp mxLookUp;
    @InjectMocks EmailExtractor emailExtractor;
//    private final MxLookUp mxLookUpStub = domain -> MxLookUp.MxStatus.VALID;
//    EmailExtractor emailExtractor = new EmailExtractor(mxLookUpStub);

    @Nested
    @DisplayName("normalizeObfuscatedEmailsInText")
    class NormalizeObfuscatedEmailsInText {
        @Test
        @DisplayName("normalizeObfuscatedEmailsInText: null -> null")
        void normalize_when_null_returns_null() {
            //Arrange
            String input = null;
            //Act
            String result = emailExtractor.normalizeObfuscatedEmailsInText(input);
            //Assert
            assertNull(result);
        }

        @Test
        @DisplayName("normalizeObfuscatedEmailsInText: blank -> unchanged")
        void normalize_when_blank_returns_unchanged() {
            //Arrange
            String input = " ";
            //Act
            String result = emailExtractor.normalizeObfuscatedEmailsInText(input);
            //Assert
            assertEquals(" ", result);
        }

        @Test
        @DisplayName("normalizeObfuscatedEmailsInText: (at) and (dot) -> @ and .")
        void normalize_parentheses_at_dot() {
            //Arrange
            String input = "kontakt: info(at)example(dot)de";
            //Act
            String result = emailExtractor.normalizeObfuscatedEmailsInText(input);
            //Assert
            assertEquals("kontakt: info@example.de", result);
        }

        @Test
        @DisplayName("normalizeObfuscatedEmailsInText: ( at ) and ( dot ) with spaces -> @ and .")
        void normalize_parentheses_at_dot_with_spaces() {
            //Arrange
            String input = "kontakt: info ( at ) example ( dot ) de";
            //Act
            String result = emailExtractor.normalizeObfuscatedEmailsInText(input);
            //Assert
            assertEquals("kontakt: info@example.de", result);
        }

        @Test
        @DisplayName("normalizeObfuscatedEmailsInText: at and dot -> @ and .")
        void normalize_parentheses_at_dot_words() {
            //Arrange
            String input = "kontakt: info at example dot de";
            //Act
            String result = emailExtractor.normalizeObfuscatedEmailsInText(input);
            //Assert
            assertEquals("kontakt: info@example.de", result);
        }

        @Test
        @DisplayName("normalizeObfuscatedEmailsInText: case sensitive")
        void normalize_case_sensitive() {
            //Arrange
            String input = "KONTAKT: INFO AT EXAMPLE DOT DE";
            //Act
            String result = emailExtractor.normalizeObfuscatedEmailsInText(input);
            //Assert
            assertEquals("KONTAKT: INFO@EXAMPLE.DE", result);
        }

        @Test
        @DisplayName("normalizeObfuscatedEmailsInText: should not change normal email")
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
    @DisplayName("normalizeObfuscatedEmailsInText")
    class normalizeEmail {

    }



}