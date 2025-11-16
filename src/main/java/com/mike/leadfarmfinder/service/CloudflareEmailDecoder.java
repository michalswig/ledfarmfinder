package com.mike.leadfarmfinder.service;

public class CloudflareEmailDecoder {

    public static String decode(String cfemail) {
        int r = Integer.parseInt(cfemail.substring(0, 2), 16);
        StringBuilder email = new StringBuilder();

        for (int n = 2; n < cfemail.length(); n += 2) {
            int c = Integer.parseInt(cfemail.substring(n, n + 2), 16) ^ r;
            email.append((char) c);
        }
        return email.toString();
    }
}
