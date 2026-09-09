package dev.chorus.core.players;

import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;

/**
 * How long somebody has played, in milliseconds.
 *
 * <p>Comes straight from the vanilla statistic, which every version from 1.18 to 26 still
 * calls {@code PLAY_ONE_MINUTE} even though it counts ticks. Reading it rather than keeping
 * our own tally means the number can never drift from the one on the statistics screen.
 */
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
