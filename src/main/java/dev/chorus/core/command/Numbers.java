package dev.chorus.core.command;

/**
 * Reading a number a player typed.
 *
 * <p>Commands take numbers as text and have to cope with being handed something else, so
 * every one of them wants the same three lines. They live here instead, which is also what
 * keeps the fallback for a bad number from being {@code -1} in one command and {@code 0} in
 * the next.
 */
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
