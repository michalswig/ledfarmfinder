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

    private final TextObfuscationNormalizer normalizer = new TextObfuscationNormalizer();

    static Stream<Arguments> getTestData() {
        return Stream.of(
                Arguments.of(" ", " "),
                Arguments.of("kontakt: info(at)example(dot)de", "kontakt: info@example.de"),
                Arguments.of("kontakt: info ( at ) example ( dot ) de", "kontakt: info@example.de"),
                Arguments.of("kontakt: info at example dot de", "kontakt: info@example.de"),
                Arguments.of("KONTAKT: INFO AT EXAMPLE DOT DE", "KONTAKT: INFO@EXAMPLE.DE"),
                Arguments.of("kontakt: info@example.de", "kontakt: info@example.de")
        );
    }

    @Test
    @DisplayName("normalize should return null when input is null")
    void shouldReturnNullWhenInputIsNull() {
        assertNull(normalizer.normalize(null));
    }

    @ParameterizedTest
    @MethodSource("getTestData")
    @DisplayName("normalize should replace obfuscated at and dot variants")
    void shouldNormalizeObfuscatedEmailParts(String input, String expected) {
        assertEquals(expected, normalizer.normalize(input));
    }
}