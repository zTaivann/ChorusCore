package dev.chorus.core.players;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * What the server remembers about a player between visits.
 *
 * @param player    their account
 * @param name      the username they last logged in with
 * @param nickname  what they asked to be called, or null
 * @param firstSeen when they first joined
 * @param lastSeen  when they were last here
 * @param address   the address they last connected from, for finding shared accounts
 * @param world     where they logged out, for /tpoffline
 */
public record PlayerProfile(UUID player, String name, @Nullable String nickname,
                            long firstSeen, long lastSeen, String address,
                            String world, double x, double y, double z, float yaw, float pitch) {

    /** Empty when the world they logged out in is gone, which happens often enough. */
    public Optional<Location> lastLocation() {
        if (world.isEmpty()) {
            return Optional.empty();
        }
        World found = Bukkit.getWorld(world);
        return found == null ? Optional.empty() : Optional.of(new Location(found, x, y, z, yaw, pitch));
    }

    public String displayName() {
        return nickname == null || nickname.isEmpty() ? name : nickname;
    }
}
