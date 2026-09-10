package dev.chorus.core.home;

import dev.chorus.core.location.StoredLocation;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** A position a player saved for themselves. A null icon means the one from the config. */
public record Home(UUID owner, String name, UUID worldId, String worldName,
                   double x, double y, double z, float yaw, float pitch,
                   long createdAt, @Nullable String icon) implements StoredLocation {

    public static Home create(UUID owner, String name, Location where) {
        World world = where.getWorld();
        return new Home(owner, name, world.getUID(), world.getName(),
                where.getX(), where.getY(), where.getZ(),
                where.getYaw(), where.getPitch(), System.currentTimeMillis(), null);
    }

    /** Moving or renaming a home keeps everything it was given, including when it was made. */
    public Home renamedTo(String newName) {
        return new Home(owner, newName, worldId, worldName, x, y, z, yaw, pitch, createdAt, icon);
    }

    public Home movedTo(Location where) {
        World world = where.getWorld();
        return new Home(owner, name, world.getUID(), world.getName(),
                where.getX(), where.getY(), where.getZ(),
                where.getYaw(), where.getPitch(), createdAt, icon);
    }

    public Home withIcon(@Nullable String value) {
        return new Home(owner, name, worldId, worldName, x, y, z, yaw, pitch, createdAt, value);
    }
}
