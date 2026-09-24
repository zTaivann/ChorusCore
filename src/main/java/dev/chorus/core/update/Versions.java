package dev.chorus.core.update;

import dev.chorus.core.command.Numbers;

/** Compares two version strings the way people read them. */
public final class Versions {

    private static final int MAX_PARTS = 6;

    private Versions() {
    }

    /** Whether {@code candidate} is a later release than {@code current}. */
    public static boolean isNewer(String candidate, String current) {
        return compare(candidate, current) > 0;
    }

    /** Above zero when {@code left} is the later of the two. */
    public static int compare(String left, String right) {
        String[] leftParts = split(left);
        String[] rightParts = split(right);

        int order = compareNumbers(leftParts[0], rightParts[0]);
        if (order != 0) {
            return order;
        }

        boolean leftFinal = leftParts[1].isEmpty();
        boolean rightFinal = rightParts[1].isEmpty();
        if (leftFinal != rightFinal) {
            return leftFinal ? 1 : -1;
        }
        return leftParts[1].compareToIgnoreCase(rightParts[1]);
    }

    private static int compareNumbers(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int length = Math.min(MAX_PARTS, Math.max(leftParts.length, rightParts.length));

        for (int part = 0; part < length; part++) {
            int order = Integer.compare(number(leftParts, part), number(rightParts, part));
            if (order != 0) {
                return order;
            }
        }
        return 0;
    }

    /** The number and whatever followed it, as two pieces. */
    private static String[] split(String version) {
        String cleaned = version.trim();
        if (cleaned.startsWith("v") || cleaned.startsWith("V")) {
            cleaned = cleaned.substring(1);
        }

        int end = 0;
        while (end < cleaned.length()
                && (Character.isDigit(cleaned.charAt(end)) || cleaned.charAt(end) == '.')) {
            end++;
        }
        return new String[] {cleaned.substring(0, end), cleaned.substring(end)};
    }

    /** A part that is missing or not a number counts as zero. */
    private static int number(String[] parts, int index) {
        return index < parts.length ? Numbers.integer(parts[index], 0) : 0;
    }
}
