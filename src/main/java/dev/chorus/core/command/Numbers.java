package dev.chorus.core.command;

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
     * An amount of money the way people write one: {@code 5}, {@code 5.50}, {@code 5,50},
     * {@code $5} or {@code 5€}. Negative when it is not an amount at all.
     */
    public static double money(String raw) {
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
        return value >= 0 ? value : -1;
    }

    /** Rounded to hundredths, which is how prices and payments have always been kept. */
    public static double cents(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }

    private static boolean isPartOfNumber(char character) {
        return (character >= '0' && character <= '9')
                || character == '.' || character == ',' || character == '-';
    }
}
