package dev.chorus.core.location;

import dev.chorus.core.storage.Queries;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/** One category of named positions, held in memory. */
public final class LocationService {

    private final String category;
    private final LocationRepository repository;
    private final Executor worker;
    private final Executor mainThread;
    private final Map<String, NamedLocation> cache = new ConcurrentHashMap<>();

    private volatile @Nullable List<NamedLocation> sorted;

    public LocationService(String category, LocationRepository repository,
                           Executor worker, Executor mainThread) {
        this.category = category;
        this.repository = repository;
        this.worker = worker;
        this.mainThread = mainThread;
    }

    /** Blocking, and meant to be: this only runs while the server is starting up. */
    public void loadAll() throws SQLException {
        Map<String, NamedLocation> loaded = new ConcurrentHashMap<>();
        for (NamedLocation location : repository.findAll(category)) {
            loaded.put(location.name(), location);
        }
        cache.clear();
        cache.putAll(loaded);
        sorted = null;
    }

    public Optional<NamedLocation> find(String name) {
        return Optional.ofNullable(cache.get(Names.normalise(name)));
    }

    public List<NamedLocation> all() {
        List<NamedLocation> snapshot = sorted;
        if (snapshot == null) {
            // Two callers racing here just build the same list twice; the result is identical.
            snapshot = cache.values().stream()
                    .sorted(Comparator.comparing(NamedLocation::name))
                    .toList();
            sorted = snapshot;
        }
        return snapshot;
    }

    public CompletableFuture<Void> save(NamedLocation location) {
        CompletableFuture<Void> saved = Queries.run(() -> {
            repository.save(category, location);
            return null;
        }, worker, mainThread);

        return saved.thenRun(() -> {
            cache.put(location.name(), location);
            sorted = null;
        });
    }

    public CompletableFuture<Boolean> delete(String name) {
        String key = Names.normalise(name);
        CompletableFuture<Boolean> deleted =
                Queries.run(() -> repository.delete(category, key), worker, mainThread);

        return deleted.thenApply(removed -> {
            if (removed) {
                cache.remove(key);
                sorted = null;
            }
            return removed;
        });
    }
}
