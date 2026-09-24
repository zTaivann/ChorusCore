package dev.chorus.core.home;

import dev.chorus.core.api.HomeApi;
import dev.chorus.core.api.event.ChorusHomeSaveEvent;
import dev.chorus.core.command.PermissionLimits;
import dev.chorus.core.location.Names;
import dev.chorus.core.storage.LoginData;
import dev.chorus.core.storage.Queries;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Keeps the homes of everyone online in memory so that reading them costs nothing, and
 * pushes every change to the database before the cache is touched. Writing first means a
 * player is never told a home was saved when it was not.
 */
public final class HomeService implements HomeApi, LoginData.Part {

    private static final String LIMIT_PREFIX = "chorus.home.limit.";
    private static final String UNLIMITED_PERMISSION = "chorus.home.unlimited";

    private final HomeRepository repository;
    private final Executor worker;
    private final Executor mainThread;
    private final Map<UUID, Map<String, Home>> cache = new ConcurrentHashMap<>();

    private volatile HomeSettings settings;

    HomeService(HomeRepository repository, Executor worker, Executor mainThread, HomeSettings settings) {
        this.repository = repository;
        this.worker = worker;
        this.mainThread = mainThread;
        this.settings = settings;
    }

    public HomeSettings settings() {
        return settings;
    }

    void apply(HomeSettings updated) {
        this.settings = updated;
    }

    @Override
    public void load(UUID owner) throws SQLException {
        Map<String, Home> homes = new ConcurrentHashMap<>();
        for (Home home : Queries.await(() -> repository.findByOwner(owner), worker)) {
            homes.put(home.name(), home);
        }
        cache.put(owner, homes);
    }

    @Override
    public void unload(UUID owner) {
        cache.remove(owner);
    }

    void clear() {
        cache.clear();
    }

    public boolean isLoaded(UUID owner) {
        return cache.containsKey(owner);
    }

    public Optional<Home> find(UUID owner, String name) {
        Map<String, Home> homes = cache.get(owner);
        return homes == null ? Optional.empty() : Optional.ofNullable(homes.get(Names.normalise(name)));
    }

    public List<Home> list(UUID owner) {
        Map<String, Home> homes = cache.get(owner);
        if (homes == null) {
            return List.of();
        }
        List<Home> sorted = new ArrayList<>(homes.values());
        sorted.sort(Comparator.comparing(Home::name));
        return sorted;
    }

    /**
     * The player's home when they have exactly one, for a bare /home. Kept apart from
     * {@link #list} so the common case does not copy and sort a list to read one entry.
     */
    public Optional<Home> onlyHome(UUID owner) {
        Map<String, Home> homes = cache.get(owner);
        if (homes == null || homes.size() != 1) {
            return Optional.empty();
        }
        return homes.values().stream().findFirst();
    }

    public int count(UUID owner) {
        Map<String, Home> homes = cache.get(owner);
        return homes == null ? 0 : homes.size();
    }

    /** How many of their homes are in one world, for the per-world caps. */
    public int countIn(UUID owner, String world) {
        Map<String, Home> homes = cache.get(owner);
        if (homes == null) {
            return 0;
        }
        int found = 0;
        for (Home home : homes.values()) {
            if (home.worldName().equalsIgnoreCase(world)) {
                found++;
            }
        }
        return found;
    }

    public CompletableFuture<Void> save(Home home) {
        Map<String, Home> owned = cache.get(home.owner());
        ChorusHomeSaveEvent event = new ChorusHomeSaveEvent(home,
                owned != null && owned.containsKey(home.name()));
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return CompletableFuture.failedFuture(new CancellationException("cancelled by a plugin"));
        }

        CompletableFuture<Void> saved = Queries.run(() -> {
            repository.save(home);
            return null;
        }, worker, mainThread);

        return saved.thenRun(() -> cache
                .computeIfAbsent(home.owner(), owner -> new ConcurrentHashMap<>())
                .put(home.name(), home));
    }

    public CompletableFuture<Boolean> delete(UUID owner, String name) {
        String key = Names.normalise(name);
        CompletableFuture<Boolean> deleted =
                Queries.run(() -> repository.delete(owner, key), worker, mainThread);

        return deleted.thenApply(removed -> {
            if (removed) {
                Map<String, Home> homes = cache.get(owner);
                if (homes != null) {
                    homes.remove(key);
                }
            }
            return removed;
        });
    }

    public boolean isValidName(String name) {
        return Names.isValid(name, settings.maxNameLength());
    }

    /** Highest {@code chorus.home.limit.<n>} the player holds, or the one in the config. */
    public int limit(Player player) {
        if (player.hasPermission(UNLIMITED_PERMISSION)) {
            return Integer.MAX_VALUE;
        }
        return PermissionLimits.highest(player, LIMIT_PREFIX, settings.defaultLimit());
    }
}
