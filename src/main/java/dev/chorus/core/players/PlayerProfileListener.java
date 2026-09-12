package dev.chorus.core.players;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerProfileListener implements Listener {

    private final PlayerProfiles profiles;

    public PlayerProfileListener(PlayerProfiles profiles) {
        this.profiles = profiles;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        profiles.arrived(event.getPlayer());
    }

    /** At the lowest priority, so the position written down is where they actually were. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        profiles.left(event.getPlayer());
    }
}
