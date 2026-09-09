package dev.chorus.core.location;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A position that came out of the database.
 *
 * <p>The world is kept twice on purpose: the id survives a rename and the name survives a
 * world that was deleted and generated again. Records implement this for free, since their
 * accessors already match.
 */
public interface StoredLocation {

    UUID worldId();

    String worldName();

    double x();

    double y();

    double z();

    float yaw();

    float pitch();

    /** Returns {@code null} while the world is not loaded. */
    default @Nullable Location toLocation() {
        World world = Bukkit.getWorld(worldId());
        if (world == null) {
            world = Bukkit.getWorld(worldName());
        }
        return world == null ? null : new Location(world, x(), y(), z(), yaw(), pitch());
    }
}
