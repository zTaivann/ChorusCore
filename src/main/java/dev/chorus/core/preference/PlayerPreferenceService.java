package dev.chorus.core.preference;

import dev.chorus.core.storage.LoginData;
import dev.chorus.core.storage.Queries;

import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The per-player preferences, held in memory for everyone online and written through to the
 * database as they change.
 */
public final class PlayerPreferenceService implements LoginData.Part {

    private final PlayerPreferenceRepository repository;
    private final Executor worker;
    private final Logger logger;
    private final Map<UUID, Set<String>> cache = new ConcurrentHashMap<>();

    public PlayerPreferenceService(PlayerPreferenceRepository repository, Executor worker,
                                   Logger logger) {
        this.repository = repository;
        this.worker = worker;
        this.logger = logger;
    }

    @Override
    public void load(UUID player) throws SQLException {
        Set<String> preferences = ConcurrentHashMap.newKeySet();
        preferences.addAll(Queries.await(() -> repository.findSet(player), worker));
        cache.put(player, preferences);
    }

    @Override
    public void unload(UUID player) {
        cache.remove(player);
    }

    public boolean isSet(UUID player, PlayerPreference preference) {
        Set<String> preferences = cache.get(player);
        return preferences != null && preferences.contains(preference.stored());
    }

    /**
     * Changes the preference at once and saves it in the background.
     */
    public boolean toggle(UUID player, PlayerPreference preference) {
        Set<String> preferences = cache.computeIfAbsent(player, key -> ConcurrentHashMap.newKeySet());
        boolean nowSet = !preferences.remove(preference.stored());
        if (nowSet) {
            preferences.add(preference.stored());
        }

        worker.execute(() -> {
            try {
                if (nowSet) {
                    repository.set(player, preference.stored());
                } else {
                    repository.clear(player, preference.stored());
                }
            } catch (SQLException exception) {
                logger.log(Level.WARNING,
                        "Could not save the '" + preference.stored() + "' preference of " + player,
                        exception);
            }
        });
        return nowSet;
    }

    public void clearAll() {
        cache.clear();
    }
}
