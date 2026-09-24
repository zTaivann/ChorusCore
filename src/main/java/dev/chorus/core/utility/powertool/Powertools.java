package dev.chorus.core.utility.powertool;

import dev.chorus.core.storage.LoginData;
import dev.chorus.core.storage.Queries;
import org.bukkit.Material;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/** The commands players have tied to the items they are holding. */
public final class Powertools implements LoginData.Part {

    /** Enough for a wand that does several things, few enough that a typo cannot run twenty. */
    public static final int MAX_COMMANDS = 5;
    public static final int MAX_LENGTH = 100;

    private final PowertoolRepository repository;
    private final Executor worker;
    private final Logger logger;
    private final Map<UUID, Map<Material, List<String>>> cache = new ConcurrentHashMap<>();

    public Powertools(PowertoolRepository repository, Executor worker, Logger logger) {
        this.repository = repository;
        this.worker = worker;
        this.logger = logger;
    }

    @Override
    public void load(UUID player) throws SQLException {
        Map<Material, List<String>> bound = new HashMap<>();
        Map<String, List<String>> rows = Queries.await(() -> repository.findAll(player), worker);
        for (Map.Entry<String, List<String>> row : rows.entrySet()) {
            Material material = Material.matchMaterial(row.getKey());
            if (material != null) {
                bound.put(material, row.getValue());
            }
        }
        cache.put(player, bound);
    }

    @Override
    public void unload(UUID player) {
        cache.remove(player);
    }

    public void clearAll() {
        cache.clear();
    }

    public List<String> on(UUID player, Material material) {
        Map<Material, List<String>> bound = cache.get(player);
        if (bound == null) {
            return List.of();
        }
        return bound.getOrDefault(material, List.of());
    }

    public Map<Material, List<String>> all(UUID player) {
        return Map.copyOf(cache.getOrDefault(player, Map.of()));
    }

    /** Replaces whatever was on that item. */
    public void bind(UUID player, Material material, String command) {
        write(player, material, List.of(command));
    }

    /**
     * Puts another command on the same item.
     *
     * @return false when the item already holds as many as it may.
     */
    public boolean add(UUID player, Material material, String command) {
        List<String> current = on(player, material);
        if (current.size() >= MAX_COMMANDS) {
            return false;
        }
        List<String> updated = new ArrayList<>(current);
        updated.add(command);
        write(player, material, updated);
        return true;
    }

    /** @return whether there was anything on it. */
    public boolean unbind(UUID player, Material material) {
        Map<Material, List<String>> bound = cache.get(player);
        if (bound == null || bound.remove(material) == null) {
            return false;
        }

        String name = key(material);
        worker.execute(() -> {
            try {
                repository.delete(player, name);
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "Could not forget the powertool of " + player, exception);
            }
        });
        return true;
    }

    /** @return how many items were cleared. */
    public int unbindAll(UUID player) {
        Map<Material, List<String>> bound = cache.get(player);
        int had = bound == null ? 0 : bound.size();
        if (had == 0) {
            return 0;
        }
        bound.clear();

        worker.execute(() -> {
            try {
                repository.deleteAll(player);
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "Could not clear the powertools of " + player, exception);
            }
        });
        return had;
    }

    private void write(UUID player, Material material, List<String> commands) {
        cache.computeIfAbsent(player, key -> new ConcurrentHashMap<>())
                .put(material, List.copyOf(commands));

        String name = key(material);
        List<String> saving = List.copyOf(commands);
        worker.execute(() -> {
            try {
                repository.save(player, name, saving);
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "Could not save the powertool of " + player, exception);
            }
        });
    }

    /** The name a material is stored under, which is the enum name and not its display name. */
    private static String key(Material material) {
        return material.name().toLowerCase(Locale.ROOT);
    }
}
