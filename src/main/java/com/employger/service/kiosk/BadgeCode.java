package com.employger.service.kiosk;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The code on an attendance badge, e.g. EMP-7Q4K-M2XP-9D3H-K8RT: "EMP" and
 * 16 random characters (80 bits), so nobody can work out a coworker's code
 * from their employee number. The alphabet leaves out 0/O and 1/I, which
 * look alike when a damaged card is typed in by hand.
 *
 * The constructor accepts only well-formed codes, so a BadgeCode in hand is
 * always valid; parse() is the forgiving way in for scanned or typed text.
 */
public record BadgeCode(String value) {

    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int RANDOM_LENGTH = 16;
    private static final int GROUP = 4;
    private static final Pattern FORMAT = Pattern.compile(
            "EMP(-[" + ALPHABET + "]{" + GROUP + "}){" + RANDOM_LENGTH / GROUP + "}");

    private static final SecureRandom RANDOM = new SecureRandom();

    public BadgeCode {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Not a badge code.");
        }
    }

    /** A new random code. */
    public static BadgeCode generate() {

        StringBuilder random = new StringBuilder(RANDOM_LENGTH);

        for (int i = 0; i < RANDOM_LENGTH; i++) {
            random.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }

        return new BadgeCode(format(random.toString()));
    }

    /**
     * Reads scanned or typed text: case, spaces and dashes don't matter, so
     * "emp 7q4k m2xp 9d3h k8rt" works. Empty if it can't be a badge code.
     */
    public static Optional<BadgeCode> parse(String text) {

        if (text == null) {
            return Optional.empty();
        }

        String compact = text.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");

        if (!compact.startsWith("EMP") || compact.length() != 3 + RANDOM_LENGTH) {
            return Optional.empty();
        }

        String formatted = format(compact.substring(3));

        return FORMAT.matcher(formatted).matches()
                ? Optional.of(new BadgeCode(formatted))
                : Optional.empty();
    }

    private static String format(String random) {

        StringBuilder code = new StringBuilder("EMP");

        for (int i = 0; i < random.length(); i += GROUP) {
            code.append('-').append(random, i, i + GROUP);
        }

        return code.toString();
    }

    @Override
    public String toString() {
        return value;
    }
}
