package dev.chorus.core.home;

import dev.chorus.core.location.StoredLocation;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/** A position a player saved for themselves. */
public record Home(UUID owner, String name, UUID worldId, String worldName,
                   double x, double y, double z, float yaw, float pitch,
                   long createdAt) implements StoredLocation {

    public static Home create(UUID owner, String name, Location where) {
        World world = where.getWorld();
        return new Home(owner, name, world.getUID(), world.getName(),
                where.getX(), where.getY(), where.getZ(),
                where.getYaw(), where.getPitch(), System.currentTimeMillis());
    }
}
