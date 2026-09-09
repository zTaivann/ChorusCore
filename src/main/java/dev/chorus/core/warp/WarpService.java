package dev.chorus.core.warp;

import dev.chorus.core.api.WarpApi;
import dev.chorus.core.location.LocationService;
import dev.chorus.core.location.NamedLocation;
import dev.chorus.core.location.Names;
import org.bukkit.permissions.Permissible;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class WarpService implements WarpApi {

    private final LocationService locations;

    private volatile WarpSettings settings;

    WarpService(LocationService locations, WarpSettings settings) {
        this.locations = locations;
        this.settings = settings;
    }

    public WarpSettings settings() {
        return settings;
    }

    void apply(WarpSettings updated) {
        this.settings = updated;
    }

    public Optional<NamedLocation> find(String name) {
        return locations.find(name);
    }

    public List<NamedLocation> all() {
        return locations.all();
    }

    /** The warps this sender is allowed to use. Everything when per-warp permissions are off. */
    public List<NamedLocation> visibleTo(Permissible who) {
        WarpSettings current = settings;
        return locations.all().stream().filter(warp -> current.canUse(who, warp.name())).toList();
    }

    public boolean isValidName(String name) {
        return Names.isValid(name, settings.maxNameLength());
    }

    public CompletableFuture<Void> save(NamedLocation warp) {
        return locations.save(warp);
    }

    public CompletableFuture<Boolean> delete(String name) {
        return locations.delete(name);
    }
}
