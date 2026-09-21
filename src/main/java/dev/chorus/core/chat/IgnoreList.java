package dev.chorus.core.chat;

import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Who each player has asked not to hear from. */
public final class IgnoreList {

    private static final String BYPASS_PERMISSION = "chorus.chat.ignore.bypass";

    private final SqlIgnoreRepository repository;
    private final Executor worker;
    private final Logger logger;
    private final Map<UUID, Set<UUID>> ignored = new ConcurrentHashMap<>();

    public IgnoreList(SqlIgnoreRepository repository, Executor worker, Logger logger) {
        this.repository = repository;
        this.worker = worker;
        this.logger = logger;
    }

    /** Blocking. Called from the login thread, before the player is let in. */
    public void load(UUID owner) throws SQLException {
        ignored.put(owner, ConcurrentHashMap.newKeySet());
        ignored.get(owner).addAll(repository.findFor(owner));
    }

    public void unload(UUID owner) {
        ignored.remove(owner);
    }

    public void clear() {
        ignored.clear();
    }

    public boolean ignores(UUID owner, UUID other) {
        Set<UUID> list = ignored.get(owner);
        return list != null && list.contains(other);
    }

    public Set<UUID> listOf(UUID owner) {
        return Set.copyOf(ignored.getOrDefault(owner, Set.of()));
    }

    public String bypassPermission() {
        return BYPASS_PERMISSION;
    }

    /**
     * Adds or removes, whichever it was not.
     *
     * @return true when they are now ignored.
     */
    public boolean toggle(UUID owner, UUID other) {
        Set<UUID> list = ignored.computeIfAbsent(owner, id -> ConcurrentHashMap.newKeySet());
        boolean added = list.add(other);
        if (!added) {
            list.remove(other);
        }

        worker.execute(() -> {
            try {
                if (added) {
                    repository.add(owner, other);
                } else {
                    repository.remove(owner, other);
                }
            } catch (SQLException failed) {
                logger.log(Level.WARNING, "Could not save the ignore list of " + owner, failed);
            }
        });
        return added;
    }
}
