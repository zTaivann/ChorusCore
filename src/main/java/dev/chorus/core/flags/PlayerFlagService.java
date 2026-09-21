package dev.chorus.core.flags;

import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The per-player switches, held in memory for everyone online and written through to the
 * database as they change.
 */
public final class PlayerFlagService {

    private final PlayerFlagRepository repository;
    private final Executor worker;
    private final Logger logger;
    private final Map<UUID, Set<String>> cache = new ConcurrentHashMap<>();

    public PlayerFlagService(PlayerFlagRepository repository, Executor worker, Logger logger) {
        this.repository = repository;
        this.worker = worker;
        this.logger = logger;
    }

    /** Blocking. Called from the login thread before the player is let in. */
    public void load(UUID player) throws SQLException {
        Set<String> flags = ConcurrentHashMap.newKeySet();
        flags.addAll(repository.findSet(player));
        cache.put(player, flags);
    }

    public void unload(UUID player) {
        cache.remove(player);
    }

    public boolean isSet(UUID player, PlayerFlag flag) {
        Set<String> flags = cache.get(player);
        return flags != null && flags.contains(flag.stored());
    }

    /**
     * Flips the switch straight away and saves it in the background. The player sees the
     * answer at once, and a database that is briefly unavailable costs them the setting on
     * their next login rather than the command they just typed.
     */
    public boolean toggle(UUID player, PlayerFlag flag) {
        Set<String> flags = cache.computeIfAbsent(player, key -> ConcurrentHashMap.newKeySet());
        boolean nowSet = !flags.remove(flag.stored());
        if (nowSet) {
            flags.add(flag.stored());
        }

        worker.execute(() -> {
            try {
                if (nowSet) {
                    repository.set(player, flag.stored());
                } else {
                    repository.clear(player, flag.stored());
                }
            } catch (SQLException exception) {
                logger.log(Level.WARNING,
                        "Could not save the '" + flag.stored() + "' setting of " + player, exception);
            }
        });
        return nowSet;
    }

    public void clearAll() {
        cache.clear();
    }
}
