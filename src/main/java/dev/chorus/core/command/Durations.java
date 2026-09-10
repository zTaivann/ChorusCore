package dev.chorus.core.command;

import java.util.concurrent.TimeUnit;

public final class Durations {

    private Durations() {
    }

    /**
     * Rounds up, so a second and a half left never shows as "1s" and then refuses again.
     *
     * <p>Two units at most, largest first. A kit on a week's cooldown reading "168h 0m" is
     * technically right and tells nobody anything.
     */
    public static String format(long millis) {
        long total = (millis + 999) / 1000;
        long days = TimeUnit.SECONDS.toDays(total);
        long hours = TimeUnit.SECONDS.toHours(total) % 24;
        long minutes = TimeUnit.SECONDS.toMinutes(total) % 60;
        long seconds = total % 60;

        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }
}
