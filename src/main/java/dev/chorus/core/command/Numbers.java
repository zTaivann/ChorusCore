package dev.chorus.core.command;

import java.util.OptionalInt;

/** Reading a number a player typed. */
public final class Numbers {

    private Numbers() {
    }

    /** The number, or {@code fallback} when it is not one. */
    public static int integer(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException | NullPointerException notANumber) {
            return fallback;
        }
    }

    /** The number, or empty when it is not one, for when no fallback can stand in for that. */
    public static OptionalInt integer(String raw) {
        try {
            return OptionalInt.of(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException | NullPointerException notANumber) {
            return OptionalInt.empty();
        }
    }

    public static long whole(String raw, long fallback) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException | NullPointerException notANumber) {
            return fallback;
        }
    }

    /** Refuses the values that are numbers to Java but not to anybody else. */
    public static double decimal(String raw, double fallback) {
        try {
            double value = Double.parseDouble(raw.trim());
            return Double.isFinite(value) ? value : fallback;
        } catch (NumberFormatException | NullPointerException notANumber) {
            return fallback;
        }
    }

    /**
     * An amount the way people write one: {@code 5}, {@code 5.50}, {@code 5,50},
     * {@code $5}, {@code 5€}, or short, {@code 1.5k} or {@code 2M}. Negative when it is not an
     * amount at all.
     */
    public static double amount(String raw) {
        if (raw == null) {
            return -1;
        }
        int start = 0;
        int end = raw.length();
        while (start < end && !isPartOfNumber(raw.charAt(start))) {
            start++;
        }
        while (end > start && !isPartOfNumber(raw.charAt(end - 1))) {
            end--;
        }
        double value = decimal(raw.substring(start, end).replace(',', '.'), -1);
        if (value < 0) {
            return -1;
        }
        double scaled = value * scale(raw.substring(end));
        return Double.isFinite(scaled) ? scaled : -1;
    }

    /**
     * The multiplier for a unit letter right after the number: k, m, b or t. Words such as
     * {@code 5mil} are not units.
     */
    private static double scale(String after) {
        if (after.isEmpty() || (after.length() > 1 && Character.isLetter(after.charAt(1)))) {
            return 1;
        }
        return switch (Character.toLowerCase(after.charAt(0))) {
            case 'k' -> 1e3;
            case 'm' -> 1e6;
            case 'b' -> 1e9;
            case 't' -> 1e12;
            default -> 1;
        };
    }

    /** Rounded to hundredths. */
    public static double cents(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }

    private static boolean isPartOfNumber(char character) {
        return (character >= '0' && character <= '9')
                || character == '.' || character == ',' || character == '-';
    }
}
