package dev.chorus.core.kits.rules;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Reads a comparison that has already had its placeholders filled in, such as
 * {@code "12 >= 10"} or {@code "Gold contains old"}.
 */
final class Comparisons {

    private static final String[] OPERATORS = {">=", "<=", "==", "!=", ">", "<"};

    private Comparisons() {
    }

    static boolean holds(String filled) {
        String contains = operatorAt(filled, "contains");
        if (contains != null) {
            String[] sides = split(filled, contains);
            return sides[0].toLowerCase(Locale.ROOT).contains(sides[1].toLowerCase(Locale.ROOT));
        }

        for (String operator : OPERATORS) {
            int at = filled.indexOf(operator);
            if (at < 0) {
                continue;
            }
            String[] sides = split(filled, operator);
            return compare(sides[0], sides[1], operator);
        }
        // No operator: read as a plain yes or no.
        String value = filled.trim();
        return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes");
    }

    private static boolean compare(String left, String right, String operator) {
        Double leftNumber = number(left);
        Double rightNumber = number(right);
        if (leftNumber != null && rightNumber != null) {
            int order = Double.compare(leftNumber, rightNumber);
            return switch (operator) {
                case ">=" -> order >= 0;
                case "<=" -> order <= 0;
                case ">" -> order > 0;
                case "<" -> order < 0;
                case "!=" -> order != 0;
                default -> order == 0;
            };
        }

        boolean same = left.equalsIgnoreCase(right);
        return switch (operator) {
            case "!=" -> !same;
            case "==" -> same;
            default -> false;
        };
    }

    private static @Nullable String operatorAt(String filled, String word) {
        return filled.toLowerCase(Locale.ROOT).contains(" " + word + " ") ? " " + word + " " : null;
    }

    private static String[] split(String filled, String operator) {
        int at = filled.toLowerCase(Locale.ROOT).indexOf(operator.toLowerCase(Locale.ROOT));
        return new String[]{
                filled.substring(0, at).trim(),
                filled.substring(at + operator.length()).trim()};
    }

    /** A currency sign is dropped, since a balance placeholder often carries one. */
    private static @Nullable Double number(String raw) {
        try {
            return Double.valueOf(raw.replace(',', '.').replace("$", ""));
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
