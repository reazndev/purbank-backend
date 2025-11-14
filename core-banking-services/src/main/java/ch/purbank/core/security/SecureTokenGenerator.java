package ch.purbank.core.security;

import java.security.SecureRandom;

public class SecureTokenGenerator {

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final String ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final String NUMERIC = "0123456789";

    public static String generateToken(int length) {
        return generate(length, ALPHANUM);
    }

    public static String generateNumeric(int length) {
        return generate(length, NUMERIC);
    }

    private static String generate(int length, String charset) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int idx = secureRandom.nextInt(charset.length());
            sb.append(charset.charAt(idx));
        }
        return sb.toString();
    }
}
