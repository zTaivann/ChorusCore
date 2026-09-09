package dev.chorus.core.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public interface TeleportApi {

    /**
     * Moves the player after the given wait, cancelling if they move or take damage, exactly
     * as the plugin's own teleports do. Pass 0 for no wait.
     */
    void teleport(Player player, Location destination, int warmupSeconds);

    /** Where the player was before their last teleport, which is what /back uses. */
    Optional<Location> previousLocation(UUID playerId);
}
