package dev.chorus.core.command;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per player, per command cooldowns. */
public final class Cooldowns {

    private final Map<UUID, Map<String, Long>> expiry = new ConcurrentHashMap<>();

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
        expiry.compute(player, (id, owned) -> {
            Map<String, Long> updated = owned == null ? new ConcurrentHashMap<>() : owned;
            updated.put(command, now + seconds * 1000L);
            return updated;
        });
    }

    /** Emptied and dropped per player as one step, so a cooldown started meanwhile survives. */
    public void sweep(long now) {
        for (UUID player : expiry.keySet()) {
            expiry.computeIfPresent(player, (id, owned) -> {
                owned.values().removeIf(until -> until <= now);
                return owned.isEmpty() ? null : owned;
            });
        }
    }

    public void clear() {
        expiry.clear();
    }
}
