package dev.chorus.core.flags;

import dev.chorus.core.locale.Messages;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PlayerFlagListener implements Listener {

    private final PlayerFlagService flags;
    private final Messages messages;
    private final Logger logger;

    public PlayerFlagListener(PlayerFlagService flags, Messages messages, Logger logger) {
        this.flags = flags;
        this.messages = messages;
        this.logger = logger;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        try {
            flags.load(event.getUniqueId());
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Could not load the settings of " + event.getName(), exception);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    messages.render("error.load-failed"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        flags.unload(event.getPlayer().getUniqueId());
    }
}
