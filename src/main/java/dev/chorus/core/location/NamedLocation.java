package dev.chorus.core.location;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/** A server-wide position with a name, such as a warp or the spawn point. */
public record NamedLocation(String name, UUID worldId, String worldName,
                            double x, double y, double z, float yaw, float pitch,
                            long createdAt) implements StoredLocation {

    public static NamedLocation create(String name, Location where) {
        World world = where.getWorld();
        return new NamedLocation(name, world.getUID(), world.getName(),
                where.getX(), where.getY(), where.getZ(),
                where.getYaw(), where.getPitch(), System.currentTimeMillis());
    }
}
