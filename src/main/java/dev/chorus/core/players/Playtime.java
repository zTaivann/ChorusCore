package dev.chorus.core.players;

import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;

/** How long somebody has played, in milliseconds. */
public final class Playtime {

    private static final long MILLIS_PER_TICK = 50;

    private Playtime() {
    }

    public static long of(OfflinePlayer player) {
        try {
            return player.getStatistic(Statistic.PLAY_ONE_MINUTE) * MILLIS_PER_TICK;
        } catch (IllegalArgumentException | IllegalStateException noDataOnFile) {
            // A player the server has a name for but no statistics file yet.
            return 0;
        }
    }
}
