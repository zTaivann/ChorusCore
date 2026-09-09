package dev.chorus.core.home;

import dev.chorus.core.locale.Messages;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

final class HomeDataListener implements Listener {

    private final HomeService homes;
    private final Messages messages;
    private final Logger logger;

    HomeDataListener(HomeService homes, Messages messages, Logger logger) {
        this.homes = homes;
        this.messages = messages;
        this.logger = logger;
    }

    /**
     * Loading here rather than on join means the data is already in memory by the time the
     * player can type anything, and a database outage turns into a clear kick instead of a
     * player who quietly appears to have lost every home.
     */
    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        try {
            homes.load(event.getUniqueId());
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Could not load the homes of " + event.getName(), exception);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, messages.render("error.load-failed"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        homes.unload(event.getPlayer().getUniqueId());
    }
}
