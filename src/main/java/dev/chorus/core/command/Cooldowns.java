package dev.chorus.core.command;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per player, per command cooldowns.
 *
 * <p>They deliberately survive a disconnect: dropping them on quit would turn every
 * cooldown into a relog away from nothing. A sweep clears the expired ones instead.
 */
public final class Cooldowns {

    private final Map<UUID, Map<String, Long>> expiry = new HashMap<>();

    /** Milliseconds left, or zero when the player may go ahead. */
    public long remaining(UUID player, String command, long now) {
        Map<String, Long> owned = expiry.get(player);
        if (owned == null) {
            return 0;
        }
        Long until = owned.get(command);
        if (until == null) {
            return 0;
        }
        if (until <= now) {
            owned.remove(command);
            return 0;
        }
        return until - now;
    }

    public void start(UUID player, String command, int seconds, long now) {
        expiry.computeIfAbsent(player, key -> new HashMap<>())
                .put(command, now + seconds * 1000L);
    }

    public void sweep(long now) {
        expiry.values().forEach(owned -> owned.values().removeIf(until -> until <= now));
        expiry.values().removeIf(Map::isEmpty);
    }

    public void clear() {
        expiry.clear();
    }
}
