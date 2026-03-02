package com.mike.leadfarmfinder.service.emailextractor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextObfuscationNormalizerTest {

    private TextObfuscationNormalizer textObfuscationNormalizer;

    @BeforeEach
    void setUp() {
        textObfuscationNormalizer = new TextObfuscationNormalizer();
    }

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

    @ParameterizedTest
    @MethodSource("getTestData")
    @DisplayName("TextObfuscationNormalizer.normalize: replaces (at)/(dot) variants with @ and .")
    void should_return_null_when_passing_null_parameter(String input, String expected) {
        assertEquals(expected, textObfuscationNormalizer.normalize(input));
    }

}