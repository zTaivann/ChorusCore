package dev.chorus.core.command;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cooldowns by owner and timer. The owner is a player, or a world or the whole server for a
 * cooldown everyone there shares; see {@link CooldownScope}.
 */
public final class Cooldowns {

    private static final String GROUP = "group:";

    private final Map<UUID, Map<String, Long>> expiry = new ConcurrentHashMap<>();

    /** The timer a command counts on: its own, or the one its group shares. */
    public static String timer(String own, String group) {
        return group.isEmpty() ? own : GROUP + group;
    }

    /** Milliseconds left, or zero when it may go ahead. */
    public long remaining(UUID owner, String timer, long now) {
        Map<String, Long> owned = expiry.get(owner);
        if (owned == null) {
            return 0;
        }
        Long until = owned.get(timer);
        if (until == null) {
            return 0;
        }
        if (until <= now) {
            owned.remove(timer);
            return 0;
        }
        return until - now;
    }

    public void start(UUID owner, String timer, int seconds, long now) {
        expiry.compute(owner, (id, owned) -> {
            Map<String, Long> updated = owned == null ? new ConcurrentHashMap<>() : owned;
            updated.put(timer, now + seconds * 1000L);
            return updated;
        });
    }

    /** Emptied and dropped per owner as one step, so a cooldown started meanwhile survives. */
    public void sweep(long now) {
        for (UUID owner : expiry.keySet()) {
            expiry.computeIfPresent(owner, (id, owned) -> {
                owned.values().removeIf(until -> until <= now);
                return owned.isEmpty() ? null : owned;
            });
        }
    }

    public void clear() {
        expiry.clear();
    }
}
