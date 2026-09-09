package dev.chorus.core.api;

import dev.chorus.core.location.NamedLocation;
import org.bukkit.permissions.Permissible;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface WarpApi {

    /** Every warp, sorted by name. */
    List<NamedLocation> all();

    Optional<NamedLocation> find(String name);

    /** The warps this sender may use, which is all of them unless per-warp permissions are on. */
    List<NamedLocation> visibleTo(Permissible who);

    CompletableFuture<Void> save(NamedLocation warp);

    CompletableFuture<Boolean> delete(String name);
}
