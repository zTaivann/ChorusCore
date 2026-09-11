package dev.chorus.core.kits.rules;

import java.util.Locale;

/**
 * Reads a comparison that has already had its placeholders filled in, such as
 * {@code "12 >= 10"} or {@code "Gold contains old"}.
 *
 * <p>Numbers are compared as numbers when both sides look like one, and as text otherwise.
 * That is what lets one line work for a level, a rank name and a yes/no, which is most of
 * what a server owner wants to gate a kit on.
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
        // No operator at all: read it as a plain yes or no, which is how most placeholders
        // that answer a question are written.
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
            // Asking whether one word is greater than another is a mistake, not a question,
            // and answering it either way would hide the mistake.
            default -> false;
        };
    }

    private static String operatorAt(String filled, String word) {
        return filled.toLowerCase(Locale.ROOT).contains(" " + word + " ") ? " " + word + " " : null;
    }

    private static String[] split(String filled, String operator) {
        int at = filled.toLowerCase(Locale.ROOT).indexOf(operator.toLowerCase(Locale.ROOT));
        return new String[]{
                filled.substring(0, at).trim(),
                filled.substring(at + operator.length()).trim()};
    }

    private static Double number(String raw) {
        try {
            return Double.valueOf(raw.replace(',', '.').replace("$", "").replace(",", ""));
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
