package com.mike.leadfarmfinder.service.emailextractor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TextObfuscationNormalizerTest {

    static Stream<Arguments> getTestData() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of(" ", " "),
                Arguments.of("kontakt: info(at)example(dot)de", "kontakt: info@example.de"),
                Arguments.of("kontakt: info ( at ) example ( dot ) de", "kontakt: info@example.de"),
                Arguments.of("kontakt: info at example dot de", "kontakt: info@example.de"),
                Arguments.of("KONTAKT: INFO AT EXAMPLE DOT DE", "KONTAKT: INFO@EXAMPLE.DE"),
                Arguments.of("kontakt: info@example.de", "kontakt: info@example.de")
        );
    }

    @Test
    @DisplayName("normalize: return null when input null")
    void normalize_null() {
        TextObfuscationNormalizer textObfuscationNormalizer = new TextObfuscationNormalizer();
        assertNull(textObfuscationNormalizer.normalize(null));
    }

    @ParameterizedTest
    @MethodSource("getTestData")
    @DisplayName("normalize: replaces obfuscated at/dot variants")
    void should_normalize_obfuscated_email_parts(String input, String expected) {
        TextObfuscationNormalizer textObfuscationNormalizer = new TextObfuscationNormalizer();
        assertEquals(expected, textObfuscationNormalizer.normalize(input));
    }

}