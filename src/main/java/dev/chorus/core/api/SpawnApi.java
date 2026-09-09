package dev.chorus.core.api;

import dev.chorus.core.location.NamedLocation;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface SpawnApi {

    /** The spawn a player in this world would be sent to, following the per-world setting. */
    Optional<NamedLocation> find(World world);

    /** Resolved and ready to teleport to, or empty when the world is not loaded. */
    Optional<Location> location(World world);

    CompletableFuture<Void> moveTo(Location where);
}
