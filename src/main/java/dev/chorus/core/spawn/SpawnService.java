package dev.chorus.core.spawn;

import dev.chorus.core.api.SpawnApi;
import dev.chorus.core.location.LocationService;
import dev.chorus.core.location.NamedLocation;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The spawn point, or one per world when the config asks for it.
 *
 * <p>Spawns share the named-location table with warps under their own category, so a warp
 * can never collide with one. The single-spawn entry keeps the name it has always had, so
 * turning per-world on and off again finds the old point still there.
 */
public final class SpawnService implements SpawnApi {

    static final String CATEGORY = "system";

    private static final String SINGLE = "spawn";

    private final LocationService locations;

    private volatile SpawnSettings settings;

    SpawnService(LocationService locations, SpawnSettings settings) {
        this.locations = locations;
        this.settings = settings;
    }

    void apply(SpawnSettings updated) {
        this.settings = updated;
    }

    public SpawnSettings settings() {
        return settings;
    }

    @Override
    public Optional<NamedLocation> find(World world) {
        SpawnSettings current = settings;
        if (!current.perWorld()) {
            return locations.find(SINGLE);
        }

        Optional<NamedLocation> own = locations.find(world.getName());
        if (own.isPresent()) {
            return own;
        }
        // A world with no spawn of its own falls back to the one named in the config,
        // and then to the single spawn, so /spawn never simply stops working.
        if (!current.fallbackWorld().isEmpty()) {
            Optional<NamedLocation> fallback = locations.find(current.fallbackWorld());
            if (fallback.isPresent()) {
                return fallback;
            }
        }
        return locations.find(SINGLE);
    }

    @Override
    public Optional<Location> location(World world) {
        return find(world).map(NamedLocation::toLocation);
    }

    @Override
    public CompletableFuture<Void> moveTo(Location where) {
        String name = settings.perWorld() ? where.getWorld().getName() : SINGLE;
        return locations.save(NamedLocation.create(name.toLowerCase(Locale.ROOT), where));
    }
}
