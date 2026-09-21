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
}
