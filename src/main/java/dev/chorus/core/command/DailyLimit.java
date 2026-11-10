package dev.chorus.core.command;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Uses per day and when the count resets.
 *
 * @param uses    0 for no limit
 * @param resetAt time of day, on the server's clock
 */
public record DailyLimit(int uses, LocalTime resetAt) {

    public static final DailyLimit NONE = new DailyLimit(0, LocalTime.MIDNIGHT);

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");
    private static final int MINUTES_PER_DAY = 24 * 60;

    public boolean limited() {
        return uses > 0;
    }

    /** {@code uses-per-day} and {@code uses-reset-at}, keeping {@code base} for what is missing. */
    public static DailyLimit read(ConfigurationSection block, DailyLimit base, String owner,
                                  Consumer<String> onProblem) {
        int uses = Math.max(0, block.getInt("uses-per-day", base.uses()));
        LocalTime reset = base.resetAt();
        Object written = block.get("uses-reset-at");
        if (written != null) {
            LocalTime read = time(written);
            if (read == null) {
                onProblem.accept(owner + " has a uses-reset-at that is not a time of day: '"
                        + shown(written) + "'. Write it in quotes, like '04:00'.");
            } else {
                reset = read;
            }
        }
        return new DailyLimit(uses, reset);
    }

    /**
     * A config time of day, or null. YAML reads an unquoted {@code 12:30} as 750, so numbers
     * are taken as minutes since midnight.
     */
    public static @Nullable LocalTime time(Object written) {
        if (written instanceof Number minutes) {
            int total = minutes.intValue();
            return total >= 0 && total < MINUTES_PER_DAY
                    ? LocalTime.of(total / 60, total % 60) : null;
        }
        try {
            return LocalTime.parse(written.toString().trim(), TIME);
        } catch (DateTimeException notATime) {
            return null;
        }
    }

    /** What was written, with a number YAML made of it turned back into hours and minutes. */
    private static String shown(Object written) {
        if (written instanceof Number minutes) {
            int total = minutes.intValue();
            return total / 60 + ":" + String.format("%02d", Math.abs(total % 60));
        }
        return String.valueOf(written);
    }
}
