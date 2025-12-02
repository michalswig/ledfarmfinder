package com.mike.leadfarmfinder.util;

import java.security.SecureRandom;

public final class TokenGenerator {

    private static final String ALPHANUM = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenGenerator() {
        // utility class
    }

    public static String generateShortToken(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int idx = RANDOM.nextInt(ALPHANUM.length());
            sb.append(ALPHANUM.charAt(idx));
        }
        return sb.toString();
    }

    public static String generateShortToken() {
        return generateShortToken(6);
    }
}