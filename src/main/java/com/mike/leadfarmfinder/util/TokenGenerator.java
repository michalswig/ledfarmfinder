package com.mike.leadfarmfinder.util;

import java.security.SecureRandom;

public final class TokenGenerator {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int DEFAULT_LENGTH = 6;

    private TokenGenerator() {
    }

    public static String generateShortToken() {
        return generateShortToken(DEFAULT_LENGTH);
    }

    public static String generateShortToken(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int idx = RANDOM.nextInt(ALPHABET.length());
            sb.append(ALPHABET.charAt(idx));
        }
        return sb.toString();
    }
}
