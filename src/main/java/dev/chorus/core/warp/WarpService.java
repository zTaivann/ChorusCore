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
    private final WarpDetailsService details;

    private volatile WarpSettings settings;

    WarpService(LocationService locations, WarpDetailsService details, WarpSettings settings) {
        this.locations = locations;
        this.details = details;
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

    /** Whether this sender may use one warp. */
    public boolean canUse(Permissible who, String warp) {
        String own = details.of(warp).permission();
        if (own != null) {
            return own.isEmpty() || who.hasPermission(own);
        }
        return settings.canUse(who, warp);
    }

    /** The warps this sender is allowed to use. */
    public List<NamedLocation> visibleTo(Permissible who) {
        return locations.all().stream().filter(warp -> canUse(who, warp.name())).toList();
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
