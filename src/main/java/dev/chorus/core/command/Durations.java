package dev.chorus.core.command;

import java.util.concurrent.TimeUnit;

public final class Durations {

    private Durations() {
    }

    /** Rounds up, so a second and a half left never shows as "1s" and then refuses again. */
    public static String format(long millis) {
        long total = (millis + 999) / 1000;
        long hours = TimeUnit.SECONDS.toHours(total);
        long minutes = TimeUnit.SECONDS.toMinutes(total) % 60;
        long seconds = total % 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }
}
