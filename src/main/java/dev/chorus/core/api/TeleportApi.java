package dev.chorus.core.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public interface TeleportApi {

    /** Teleports after the given wait (0 for none), cancelling on movement or damage like the plugin's own. */
    void teleport(Player player, Location destination, int warmupSeconds);

    /** Where the player was before their last teleport, which is what /back uses. */
    Optional<Location> previousLocation(UUID playerId);
}
