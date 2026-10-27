package com.employger.service.password;

import java.security.SecureRandom;

/**
 * A one-time password an administrator gives to someone who forgot theirs.
 * Three groups of five characters, e.g. "k7mPq-2xRtw-9bNcd": about 86 bits
 * of randomness, without characters that are easy to misread (0/O, 1/l/I).
 *
 * toString() is overridden so the password can't end up in a log line or
 * an error message by accident; only value() gives it out.
 */
public record TemporaryPassword(String value) {

    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int GROUPS = 3;
    private static final int GROUP_LENGTH = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static TemporaryPassword generate() {
        StringBuilder password = new StringBuilder();

        for (int group = 0; group < GROUPS; group++) {
            if (group > 0) {
                password.append('-');
            }
            for (int i = 0; i < GROUP_LENGTH; i++) {
                password.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
        }

        return new TemporaryPassword(password.toString());
    }

    @Override
    public String toString() {
        return "TemporaryPassword[hidden]";
    }
}
