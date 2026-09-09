package dev.chorus.core.spawn;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

final class SpawnListener implements Listener {

    private final SpawnService spawn;

    SpawnListener(SpawnService spawn) {
        this.spawn = spawn;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!spawn.settings().teleportOnFirstJoin() || player.hasPlayedBefore()) {
            return;
        }
        spawn.location(player.getWorld()).ifPresent(player::teleportAsync);
    }

    /** A bed or an anchor is the player's own choice, so it always wins over the config. */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!spawn.settings().teleportOnRespawn() || event.isBedSpawn() || event.isAnchorSpawn()) {
            return;
        }
        Location destination = spawn.location(event.getPlayer().getWorld()).orElse(null);
        if (destination != null) {
            event.setRespawnLocation(destination);
        }
    }
}
